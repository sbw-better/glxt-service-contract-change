package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.IndexStatusResponse;
import com.citics.glxt.contractchange.util.ChangeTypeCodes;
import com.citics.glxt.contractchange.util.VectorCodec;
import com.citics.glxt.contractchange.util.VectorUtils;
import com.citics.glxt.contractchange.vector.ParagraphSearchResult;
import com.citics.glxt.contractchange.vector.ParagraphVectorSample;
import com.citics.glxt.contractchange.vector.VectorIndexSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * <p>它把Oracle中的历史段落和向量加载到Java内存，预测时直接在内存中比较，不需要每次查询数据库。</p>
 *
 * <p>重新加载时先在临时变量中把新索引完整建好，加载期间预测仍使用旧索引；全部完成后再一次性
 * 切换到新索引，因此预测请求不会读到只加载了一部分的数据。</p>
 */
@Slf4j
@Service
public class ParagraphVectorIndexService {
    private final ContractParagraphMapper mapper;
    private final ContractChangeProperties properties;
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
            reload();
        } catch (RuntimeException ex) {
            log.error("应用启动时加载历史段落向量失败，索引状态已标记为LOAD_FAILED", ex);
        }
    }

    /**
     * 从Oracle重新构建当前模型版本的内存索引。
     *
     * <p>只读取“当前模型版本、当前维度并且已经生效”的记录。每条数据库BLOB先还原成float数组，
     * 然后分别放入“语义比较列表”和“文本Hash快速查找表”。</p>
     *
     * @return 新索引状态
     */
    public synchronized IndexStatusResponse reload() {
        long started = System.currentTimeMillis();
        int dimension = properties.getEmbedding().getDimension();
        String modelVersion = properties.getEmbedding().getModelVersion();
        log.info("历史段落向量索引加载开始, modelVersion={}, dimension={}", modelVersion, dimension);
        List<ContractParagraphDO> rows;
        try {
            rows = mapper.selectActiveParagraphs(modelVersion, dimension);
        } catch (RuntimeException ex) {
            // 首次加载失败不能伪装成正常空库；已有可用快照时仍继续保留旧快照。
            if (current.get().getSamples().isEmpty()) {
                current.set(VectorIndexSnapshot.loadFailed());
            }
            log.error("历史段落向量索引查询失败, modelVersion={}, dimension={}, elapsedMs={}",
                    modelVersion, dimension, System.currentTimeMillis() - started, ex);
            throw ex;
        }
        // samples供语义相似度遍历；hashIndex供完全相同段落快速命中。
        List<ParagraphVectorSample> samples = new ArrayList<ParagraphVectorSample>(rows.size());
        Map<String, ParagraphVectorSample> hashIndex = new HashMap<String, ParagraphVectorSample>();
        int errors = 0;
        for (ContractParagraphDO row : rows) {
            try {
                float[] vector = VectorCodec.decode(row.getVectorData(), dimension);
                VectorUtils.normalize(vector);
                ParagraphVectorSample sample = new ParagraphVectorSample(row.getId(), row.getOriginalText(),
                        row.getTextHash(),
                        ChangeTypeCodes.parse(row.getChangeTypeCodes()), vector);
                samples.add(sample);
                hashIndex.put(sample.getTextHash(), sample);
            } catch (RuntimeException ex) {
                errors++;
                log.error("历史段落向量加载失败, sampleId={}", row.getId(), ex);
            }
        }
        String status;
        if (rows.isEmpty()) {
            status = "EMPTY";
        } else if (errors > 0) {
            // 包括“数据库有记录但全部损坏”的情况，不能误报为正常空库。
            status = "DEGRADED";
        } else {
            status = "READY";
        }
        VectorIndexSnapshot replacement = new VectorIndexSnapshot(
                Collections.unmodifiableList(samples),
                Collections.unmodifiableMap(hashIndex), status, errors, new Date());
        // 新索引完整建好后再整体替换，切换动作很短，不会影响正在执行的预测。
        current.set(replacement);
        log.info("历史段落向量索引加载完成, status={}, dbRowCount={}, sampleCount={}, errorCount={}, elapsedMs={}",
                status, rows.size(), samples.size(), errors, System.currentTimeMillis() - started);
        return status(replacement);
    }

    /** 根据整理后段落的Hash直接查找；相同段落不需要再调用模型。 */
    public ParagraphVectorSample exact(String textHash) {
        return current.get().getHashIndex().get(textHash);
    }

    /**
     * 将新段落向量与当前内存中的历史向量逐条比较，保留最相似的前几条。
     *
     * <p>历史向量和新向量都已经归一化，所以两个向量逐项相乘后相加，得到的就是余弦相似度。
     * 这里只保留需要的前K条，不对全部历史记录做完整排序，样本较多时会更省时间。</p>
     */
    public List<ParagraphSearchResult> search(float[] query, int topK) {
        long started = System.currentTimeMillis();
        if (query == null || query.length != properties.getEmbedding().getDimension()) {
            throw new ContractChangeBusinessException("查询向量维度不正确");
        }
        if (topK <= 0) {
            return Collections.emptyList();
        }
        PriorityQueue<ParagraphSearchResult> heap = new PriorityQueue<ParagraphSearchResult>(topK,
                Comparator.comparingDouble(ParagraphSearchResult::getSimilarity));
        for (ParagraphVectorSample sample : current.get().getSamples()) {
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
                current.get().getSamples().size(), topK, results.size(),
                System.currentTimeMillis() - started);
        return results;
    }

    /** 返回当前正在提供查询服务的快照状态。 */
    public IndexStatusResponse status() {
        return status(current.get());
    }

    /** 将内部不可变快照转换为不暴露向量和正文的运维响应。 */
    private IndexStatusResponse status(VectorIndexSnapshot snapshot) {
        return new IndexStatusResponse(snapshot.getStatus(), snapshot.getSamples().size(),
                properties.getEmbedding().getModelVersion(), properties.getEmbedding().getDimension(),
                snapshot.getLoadedAt(), snapshot.getErrorCount());
    }
}
