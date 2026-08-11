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
 * <p>索引以不可变快照保存。重载期间查询继续使用旧快照，只有新快照完整构建后才通过
 * {@link AtomicReference} 一次性替换，因此预测线程不会观察到半成品索引。</p>
 */
@Slf4j
@Service
public class ParagraphVectorIndexService {
    private final ContractParagraphMapper mapper;
    private final ContractChangeProperties properties;
    private final AtomicReference<VectorIndexSnapshot> current =
            new AtomicReference<VectorIndexSnapshot>(VectorIndexSnapshot.empty());

    /** 注入历史样本 Mapper 和当前模型配置。 */
    public ParagraphVectorIndexService(ContractParagraphMapper mapper, ContractChangeProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /** 应用启动时尝试从 Oracle 恢复索引；失败不阻止应用启动。 */
    @PostConstruct
    public void initialize() {
        try {
            reload();
        } catch (RuntimeException ex) {
            log.error("应用启动时加载历史段落向量失败，暂时保留空索引", ex);
        }
    }

    /**
     * 从 Oracle 重新构建当前模型版本的内存索引。
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
            log.error("历史段落向量索引查询失败, modelVersion={}, dimension={}, elapsedMs={}",
                    modelVersion, dimension, System.currentTimeMillis() - started, ex);
            throw ex;
        }
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
        String status = samples.isEmpty() ? "EMPTY" : (errors == 0 ? "READY" : "DEGRADED");
        VectorIndexSnapshot replacement = new VectorIndexSnapshot(
                Collections.unmodifiableList(samples),
                Collections.unmodifiableMap(hashIndex), status, errors, new Date());
        // 所有记录处理完毕后原子切换；构建阶段发生未捕获异常时旧快照保持不变。
        current.set(replacement);
        log.info("历史段落向量索引加载完成, status={}, dbRowCount={}, sampleCount={}, errorCount={}, elapsedMs={}",
                status, rows.size(), samples.size(), errors, System.currentTimeMillis() - started);
        return status(replacement);
    }

    /** 按规范化文本 Hash 进行 O(1) 精确匹配。 */
    public ParagraphVectorSample exact(String textHash) {
        return current.get().getHashIndex().get(textHash);
    }

    /**
     * 对当前快照执行精确余弦相似度检索。
     *
     * <p>样本向量和查询向量均已 L2 归一化，因此点积就是余弦相似度。使用固定大小最小堆，
     * 将排序开销从全量排序降为 O(n log k)。</p>
     */
    public List<ParagraphSearchResult> search(float[] query, int topK, double minSimilarity) {
        long started = System.currentTimeMillis();
        if (query == null || query.length != properties.getEmbedding().getDimension()) {
            throw new ContractChangeBusinessException("查询向量维度不正确");
        }
        if (topK <= 0) return Collections.emptyList();
        PriorityQueue<ParagraphSearchResult> heap = new PriorityQueue<ParagraphSearchResult>(topK,
                Comparator.comparingDouble(ParagraphSearchResult::getSimilarity));
        for (ParagraphVectorSample sample : current.get().getSamples()) {
            double similarity = VectorUtils.dot(query, sample.getVector());
            if (similarity < minSimilarity) continue;
            ParagraphSearchResult result = new ParagraphSearchResult(sample, similarity);
            if (heap.size() < topK) heap.offer(result);
            else if (similarity > heap.peek().getSimilarity()) {
                heap.poll();
                heap.offer(result);
            }
        }
        List<ParagraphSearchResult> results = new ArrayList<ParagraphSearchResult>(heap);
        results.sort(Comparator.comparingDouble(ParagraphSearchResult::getSimilarity).reversed());
        log.debug("内存向量检索完成, sampleCount={}, topK={}, resultCount={}, minSimilarity={}, elapsedMs={}",
                current.get().getSamples().size(), topK, results.size(), minSimilarity,
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
