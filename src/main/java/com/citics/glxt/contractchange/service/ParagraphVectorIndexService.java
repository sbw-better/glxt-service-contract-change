package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.IndexStatusResponse;
import com.citics.glxt.contractchange.util.ChangeTypeCodes;
import com.citics.glxt.contractchange.util.VectorCodec;
import com.citics.glxt.contractchange.util.VectorUtils;
import com.citics.glxt.contractchange.util.ContractTextNormalizer;
import com.citics.glxt.contractchange.util.HashUtils;
import com.citics.glxt.contractchange.vector.ParagraphSearchResult;
import com.citics.glxt.contractchange.vector.ParagraphVectorSample;
import com.citics.glxt.contractchange.vector.VectorIndexSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.citics.glxt.contractchange.domain.SampleLibraryControlDO;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM 内存向量索引服务。
 *
 * <p>历史向量在内存比较；每次预测先读取持久化控制身份，再固定一份完整快照。</p>
 *
 * <p>重新加载时先在临时变量中把新索引完整建好，加载期间预测仍使用旧索引；全部完成后再一次性
 * 切换到新索引，因此预测请求不会读到只加载了一部分的数据。</p>
 */
@Slf4j
@Service
public class ParagraphVectorIndexService {
    private final ContractParagraphMapper mapper;
    private final ContractChangeProperties properties;
    private SampleLibraryStore store;
    private final ThreadLocal<VectorIndexSnapshot> predictionSnapshot = new ThreadLocal<>();

    @Autowired
    public void setStore(SampleLibraryStore store) { this.store=store; }
    private final AtomicReference<VectorIndexSnapshot> current =
            new AtomicReference<VectorIndexSnapshot>(VectorIndexSnapshot.notReady());

    /** 注入历史样本 Mapper 和当前模型配置。 */
    public ParagraphVectorIndexService(ContractParagraphMapper mapper, ContractChangeProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /** 应用启动后自动从Oracle恢复索引；失败时保留明确状态，方便通过状态接口排查。 */
    @PostConstruct
    public void initialize() {
        try {
            if (store != null) store.recoverInterrupted();
            reload();
        } catch (RuntimeException ex) {
            if ("NOT_READY".equals(current.get().getStatus())) {
                current.set(VectorIndexSnapshot.loadFailed());
            }
            log.error("应用启动时加载历史段落向量失败，索引状态已标记为LOAD_FAILED", ex);
        }
    }

    /**
     * 从Oracle重新构建当前模型版本的内存索引。
     *
     * <p>只读取“当前模型版本、当前维度并且已经生效”的记录。每条数据库CLOB JSON先还原成float数组，
     * 然后分别放入“语义比较列表”和“文本Hash快速查找表”。</p>
     *
     * @return 新索引状态
     */
    public synchronized IndexStatusResponse reload() {
        if (store == null) return reloadForOperation(null,null);
        SampleLibraryControlDO control=store.control();
        if (control!=null && control.getOperationId()!=null) {
            if (!"DB_COMMITTED".equals(control.getOperationStatus()))
                throw new ContractChangeBusinessException(409,"重建期间禁止重载，operationId="+control.getOperationId());
            return reloadForOperation(control.getOperationId(),control.getExpectedSamples());
        }
        String operationId=UUID.randomUUID().toString();
        store.claim(operationId,properties.getEmbedding());
        try { return reloadForOperation(operationId,null); }
        finally { store.release(operationId); }
    }

    synchronized IndexStatusResponse reloadForOperation(String operationId,Integer expectedSize) {
        long started = System.currentTimeMillis();
        int dimension = properties.getEmbedding().getDimension();
        String modelVersion = properties.getEmbedding().getModelVersion();
        String modelName=properties.getEmbedding().getModelName();
        long revision=0;
        SampleLibraryControlDO control=store==null ? null : store.control();
        if (store!=null && (control==null || !Objects.equals(operationId,control.getOperationId())))
            throw new ContractChangeBusinessException(409,"索引重载未持有写入占用");
        if (store!=null && !SampleLibraryStore.validControl(control))
            throw new ContractChangeBusinessException(409,"样本库控制状态或模型身份异常，不能重载索引");
        if (control!=null && "DB_COMMITTED".equals(control.getOperationStatus())) {
            // 恢复条件以持久化状态为准，不能被调用方传入null降级成普通重载。
            expectedSize=control.getExpectedSamples();
        }
        if (control!=null && control.getModelVersion()!=null) {
            dimension=control.getVectorDim(); modelVersion=control.getModelVersion();
            modelName=control.getModelName(); revision=control.getRevision();
        }
        String logOperationId = operationId == null ? "startup" : operationId;
        log.info("历史段落向量索引加载开始, operationId={}, modelVersion={}, dimension={}",
                logOperationId, modelVersion, dimension);
        List<ContractParagraphDO> rows;
        try {
            rows = mapper.selectActiveParagraphs(modelVersion, dimension);
        } catch (RuntimeException ex) {
            // 首次加载失败不能伪装成正常空库；已有可用快照时仍继续保留旧快照。
            if (current.get().getSamples().isEmpty() || !snapshotCompatible(current.get())) {
                current.set(VectorIndexSnapshot.loadFailed());
            }
            log.error("历史段落向量索引查询失败, operationId={}, modelVersion={}, dimension={}, elapsedMs={}",
                    logOperationId, modelVersion, dimension, System.currentTimeMillis() - started, ex);
            throw ex;
        }
        // samples供语义相似度遍历；hashIndex供完全相同段落快速命中。
        List<ParagraphVectorSample> samples = new ArrayList<ParagraphVectorSample>(rows.size());
        Map<String, ParagraphVectorSample> hashIndex = new HashMap<String, ParagraphVectorSample>();
        int errors = 0;
        for (ContractParagraphDO row : rows) {
            String stage = "正文与标签校验";
            try {
                String normalized = ContractTextNormalizer.normalize(row.getOriginalText());
                String canonicalCodes = ChangeTypeCodes.canonicalize(row.getChangeTypeCodes());
                if (normalized.isEmpty()) throw new IllegalArgumentException("规范化正文为空");
                if (normalized.length() > properties.getSearch().getMaxParagraphLength())
                    throw new IllegalArgumentException("规范化正文超过当前长度上限");
                if (!Objects.equals(normalized, row.getNormalizedText()))
                    throw new IllegalArgumentException("规范化正文与数据库字段不一致");
                if (!Objects.equals(HashUtils.sha256(normalized), row.getTextHash()))
                    throw new IllegalArgumentException("正文Hash校验失败");
                if (!Objects.equals(canonicalCodes, row.getChangeTypeCodes()))
                    throw new IllegalArgumentException("变更类型编码不规范或无效");
                if (!Objects.equals(modelVersion, row.getModelVersion()))
                    throw new IllegalArgumentException("模型版本与当前索引不一致");
                if (!Objects.equals(dimension, row.getVectorDim()))
                    throw new IllegalArgumentException("向量维度与当前模型配置不一致");
                if (!Integer.valueOf(1).equals(row.getEnabled()))
                    throw new IllegalArgumentException("样本未启用");
                if (hashIndex.containsKey(row.getTextHash()))
                    throw new IllegalArgumentException("同一模型版本下存在重复正文Hash");
                stage = "向量JSON解码";
                float[] vector = VectorCodec.decode(row.getVectorData(), dimension);
                stage = "向量归一化";
                VectorUtils.normalize(vector);
                ParagraphVectorSample sample = new ParagraphVectorSample(row.getId(), row.getOriginalText(),
                        row.getTextHash(),
                        ChangeTypeCodes.parse(row.getChangeTypeCodes()), vector);
                samples.add(sample);
                hashIndex.put(sample.getTextHash(), sample);
            } catch (RuntimeException ex) {
                errors++;
                // 只记录样本主键、校验阶段和异常说明，不记录合同正文或向量CLOB。
                log.error("历史段落向量加载失败, operationId={}, sampleId={}, stage={}, errorType={}, reason={}",
                        logOperationId, row.getId(), stage, ex.getClass().getSimpleName(),
                        ex.getMessage());
            }
        }
        String status;
        if (errors > 0) {
            // 包括“数据库有记录但全部损坏”的情况，不能误报为正常空库。
            status = "DEGRADED";
        } else if (rows.isEmpty()) {
            status = "EMPTY";
        } else {
            status = "READY";
        }
        if (expectedSize!=null && (errors!=0 || samples.size()!=expectedSize || hashIndex.size()!=expectedSize))
            throw new ContractChangeBusinessException(503,"新版本索引不完整，等待重载恢复");
        VectorIndexSnapshot replacement = new VectorIndexSnapshot(
                Collections.unmodifiableList(samples),
                Collections.unmodifiableMap(hashIndex), status, errors, new Date(),modelName,modelVersion,dimension,revision);
        // 新索引完整建好后再整体替换，切换动作很短，不会影响正在执行的预测。
        current.set(replacement);
        if (expectedSize!=null && store!=null) store.complete(operationId);
        log.info("历史段落向量索引加载完成, operationId={}, status={}, dbRowCount={}, sampleCount={}, errorCount={}, elapsedMs={}",
                logOperationId, status, rows.size(), samples.size(), errors,
                System.currentTimeMillis() - started);
        return status(replacement);
    }

    /** 根据整理后段落的Hash直接查找；相同段落不需要再调用模型。 */
    public ParagraphVectorSample exact(String textHash) {
        return boundSnapshot().getHashIndex().get(textHash);
    }

    /**
     * 将新段落向量与当前内存中的历史向量逐条比较，保留最相似的前几条。
     *
     * <p>历史向量和新向量都已经归一化，所以两个向量逐项相乘后相加，得到的就是余弦相似度。
     * 这里只保留需要的前K条，不对全部历史记录做完整排序，样本较多时会更省时间。</p>
     */
    public List<ParagraphSearchResult> search(float[] query, int topK) {
        long started = System.currentTimeMillis();
        VectorIndexSnapshot snapshot=boundSnapshot();
        if (query == null || query.length != snapshot.getVectorDimension()) {
            throw new ContractChangeBusinessException("查询向量维度不正确");
        }
        if (topK <= 0) {
            return Collections.emptyList();
        }
        PriorityQueue<ParagraphSearchResult> heap = new PriorityQueue<ParagraphSearchResult>(topK,
                Comparator.comparingDouble(ParagraphSearchResult::getSimilarity));
        for (ParagraphVectorSample sample : snapshot.getSamples()) {
            double similarity = VectorUtils.dot(query, sample.getVector());
            ParagraphSearchResult result = new ParagraphSearchResult(sample, similarity);
            if (heap.size() < topK) {
                heap.offer(result);
            } else if (similarity > heap.peek().getSimilarity()) {
                heap.poll();
                heap.offer(result);
            }
        }
        List<ParagraphSearchResult> results = new ArrayList<ParagraphSearchResult>(heap);
        results.sort(Comparator.comparingDouble(ParagraphSearchResult::getSimilarity).reversed());
        log.debug("内存向量检索完成, sampleCount={}, topK={}, resultCount={}, elapsedMs={}",
                snapshot.getSamples().size(), topK, results.size(),
                System.currentTimeMillis() - started);
        return results;
    }

    /** 返回当前正在提供查询服务的快照状态。 */
    public IndexStatusResponse status() {
        return status(boundSnapshot());
    }

    /** 将内部不可变快照转换为不暴露向量和正文的运维响应。 */
    private IndexStatusResponse status(VectorIndexSnapshot snapshot) {
        IndexStatusResponse response=new IndexStatusResponse(snapshot.getStatus(), snapshot.getSamples().size(),
                snapshot.getModelVersion(), snapshot.getVectorDimension(),
                snapshot.getLoadedAt(), snapshot.getErrorCount());
        response.setConfiguredModelVersion(properties.getEmbedding().getModelVersion());
        try {
            SampleLibraryControlDO control=store==null ? null : store.control();
            response.setActiveModelVersion(control==null ? snapshot.getModelVersion() : control.getModelVersion());
            response.setOperationId(control==null ? null : control.getOperationId());
            response.setOperationStatus(control==null ? null : control.getOperationStatus());
            response.setRecognitionAvailable(available(snapshot,control));
        } catch (RuntimeException ex) { response.setRecognitionAvailable(false); }
        return response;
    }

    /** 每次批量预测固定完整快照，精确命中也必须先通过版本检查。 */
    public void beginPrediction() {
        VectorIndexSnapshot snapshot=current.get();
        try {
            if (!available(snapshot,store==null ? null : store.control()))
                throw new ContractChangeBusinessException(503,"模型配置与有效样本库或索引不兼容，识别暂不可用");
            predictionSnapshot.set(snapshot);
        } catch (ContractChangeBusinessException ex) { throw ex; }
        catch (RuntimeException ex) { throw new ContractChangeBusinessException(503,"样本库版本控制不可用"); }
    }
    public void endPrediction() { predictionSnapshot.remove(); }
    private VectorIndexSnapshot boundSnapshot() {
        VectorIndexSnapshot snapshot=predictionSnapshot.get();
        return snapshot==null ? current.get() : snapshot;
    }
    private boolean snapshotCompatible(VectorIndexSnapshot snapshot) {
        return Objects.equals(snapshot.getModelVersion(),properties.getEmbedding().getModelVersion())
            && Objects.equals(snapshot.getModelName(),properties.getEmbedding().getModelName())
            && snapshot.getVectorDimension()==properties.getEmbedding().getDimension();
    }
    private boolean available(VectorIndexSnapshot snapshot,SampleLibraryControlDO control) {
        if (!snapshotCompatible(snapshot) || "NOT_READY".equals(snapshot.getStatus())
                || "LOAD_FAILED".equals(snapshot.getStatus()) || ("DEGRADED".equals(snapshot.getStatus())
                    && snapshot.getSamples().isEmpty())) return false;
        if (store==null) return true;
        if (!SampleLibraryStore.validControl(control)) return false;
        if (control.getModelVersion()==null) return "EMPTY".equals(snapshot.getStatus()) && mapper.countAllParagraphs()==0;
        if (!SampleLibraryStore.compatible(control,properties.getEmbedding())
                || !Objects.equals(control.getModelVersion(),snapshot.getModelVersion())) return false;
        if ("DB_COMMITTED".equals(control.getOperationStatus())) return false;
        return true;
    }
}
