package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.common.constants.CommonConstants;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.model.IndexStatusResponse;
import com.citics.glxt.contractchange.model.PredictionReference;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.util.ContractTextNormalizer;
import com.citics.glxt.contractchange.util.HashUtils;
import com.citics.glxt.contractchange.vector.ParagraphSearchResult;
import com.citics.glxt.contractchange.vector.ParagraphVectorSample;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 新合同段落变更类型预测服务。
 *
 * <p>先查找是否存在内容完全相同的历史段落；只有没有完全命中时，才调用模型生成向量，
 * 再从最相似的历史段落已有类型中综合判断新段落可能对应的类型。</p>
 */
@Slf4j
@Service
public class ContractParagraphPredictionService {
    private static final int MAX_EMBEDDING_BATCH_SIZE = 16;

    private final ParagraphVectorIndexService indexService;
    private final EmbeddingClient embeddingClient;
    private final ContractChangeProperties properties;

    /** 注入内存索引、Embedding 客户端和检索阈值配置。 */
    public ContractParagraphPredictionService(ParagraphVectorIndexService indexService,
                                              EmbeddingClient embeddingClient,
                                              ContractChangeProperties properties) {
        this.indexService = indexService;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    /**
     * 预测一个已切分合同段落对应的多个变更类型编码。
     *
     * @param paragraph 新合同段落原文
     * @param userId 当前实际操作人的用户标识，只在需要语义向量时透传给模型网关
     * @return 匹配方式、类型得分以及参考历史段落
     */
    public PredictionResponse predict(String paragraph, String userId) {
        return predictBatch(Collections.singletonList(paragraph), userId).get(0);
    }

    /**
     * 按输入顺序批量预测多个段落。精确命中不调用模型，其余段落按网关上限分批生成向量。
     */
    public List<PredictionResponse> predictBatch(List<String> paragraphs, String userId) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return Collections.emptyList();
        }
        long started = System.currentTimeMillis();
        List<String> normalizedParagraphs = new ArrayList<String>(paragraphs.size());
        List<PredictionResponse> responses = new ArrayList<PredictionResponse>(
                Collections.nCopies(paragraphs.size(), (PredictionResponse) null));
        List<Integer> semanticIndexes = new ArrayList<Integer>();
        int exactCount = 0;

        for (int i = 0; i < paragraphs.size(); i++) {
            String normalized = normalizeAndValidate(paragraphs.get(i));
            normalizedParagraphs.add(normalized);
            ParagraphVectorSample exactSample = indexService.exact(HashUtils.sha256(normalized));
            if (exactSample == null) {
                semanticIndexes.add(i);
            } else {
                responses.set(i, exact(exactSample));
                exactCount++;
            }
        }

        if (!semanticIndexes.isEmpty()) {
            predictSemanticBatches(normalizedParagraphs, semanticIndexes, responses, userId);
        }
        log.info("合同段落批量预测完成, total={}, exactCount={}, semanticCount={}, batchSize={}, elapsedMs={}",
                paragraphs.size(), exactCount, semanticIndexes.size(), effectiveBatchSize(),
                System.currentTimeMillis() - started);
        return responses;
    }

    /**
     * 集成到合同比对流程时使用的宽容批量预测。一个模型批次失败时只标记该批输入，
     * 不丢失其他批次和Hash精确命中的结果。
     */
    public List<LenientPrediction> predictBatchLenient(List<String> paragraphs, String userId) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<String>(paragraphs.size());
        List<LenientPrediction> results = new ArrayList<LenientPrediction>(
                Collections.nCopies(paragraphs.size(), (LenientPrediction) null));
        List<Integer> semanticIndexes = new ArrayList<Integer>();
        for (int index = 0; index < paragraphs.size(); index++) {
            try {
                String value = normalizeAndValidate(paragraphs.get(index));
                normalized.add(value);
                ParagraphVectorSample exactSample = indexService.exact(HashUtils.sha256(value));
                if (exactSample == null) {
                    semanticIndexes.add(index);
                } else {
                    results.set(index, LenientPrediction.success(exact(exactSample)));
                }
            } catch (RuntimeException ex) {
                normalized.add(ContractTextNormalizer.normalize(paragraphs.get(index)));
                results.set(index, LenientPrediction.failed("INVALID_INPUT"));
            }
        }
        if (semanticIndexes.isEmpty()) {
            return results;
        }

        IndexStatusResponse status = indexService.status();
        if ("EMPTY".equals(status.getStatus())) {
            for (Integer index : semanticIndexes) {
                results.set(index, LenientPrediction.success(
                        empty(0D, Collections.<PredictionReference>emptyList())));
            }
            return results;
        }
        if ("NOT_READY".equals(status.getStatus()) || "LOAD_FAILED".equals(status.getStatus())
                || status.getSampleCount() == 0) {
            for (Integer index : semanticIndexes) {
                results.set(index, LenientPrediction.failed("INDEX_UNAVAILABLE"));
            }
            return results;
        }

        int batchSize = effectiveBatchSize();
        for (int start = 0; start < semanticIndexes.size(); start += batchSize) {
            int end = Math.min(start + batchSize, semanticIndexes.size());
            List<String> texts = new ArrayList<String>(end - start);
            for (int cursor = start; cursor < end; cursor++) {
                texts.add(normalized.get(semanticIndexes.get(cursor)));
            }
            try {
                EmbeddingBatchResult embedded = embeddingClient.embed(texts, userId);
                if (embedded == null || embedded.getVectors() == null
                        || embedded.getVectors().size() != texts.size()) {
                    throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                            "Embedding返回数量与输入数量不一致");
                }
                for (int cursor = start; cursor < end; cursor++) {
                    int resultIndex = semanticIndexes.get(cursor);
                    results.set(resultIndex, LenientPrediction.success(
                            predictFromVector(embedded.getVectors().get(cursor - start))));
                }
            } catch (RuntimeException ex) {
                log.warn("合同比对类型识别批次失败, batchSize={}, exception={}",
                        texts.size(), ex.getClass().getSimpleName());
                for (int cursor = start; cursor < end; cursor++) {
                    results.set(semanticIndexes.get(cursor),
                            LenientPrediction.failed("EMBEDDING_UNAVAILABLE"));
                }
            }
        }
        return results;
    }

    /** 宽容批量预测的单项结果，不向调用方暴露底层异常。 */
    @Getter
    @AllArgsConstructor
    public static class LenientPrediction {
        private final PredictionResponse prediction;
        private final String errorCode;

        public static LenientPrediction success(PredictionResponse prediction) {
            return new LenientPrediction(prediction, null);
        }

        public static LenientPrediction failed(String errorCode) {
            return new LenientPrediction(null, errorCode);
        }

        public boolean isSuccess() {
            return prediction != null;
        }
    }

    /** 校验业务字符上限并返回规范化文本，始终拒绝空段落和超长段落。 */
    private String normalizeAndValidate(String paragraph) {
        String normalized = ContractTextNormalizer.normalize(paragraph);
        if (normalized.isEmpty()) {
            throw new ContractChangeBusinessException("合同段落不能为空");
        }
        if (normalized.length() > properties.getSearch().getMaxParagraphLength()) {
            throw new ContractChangeBusinessException(
                    "合同段落不能超过" + properties.getSearch().getMaxParagraphLength() + "字符");
        }
        return normalized;
    }

    /** 对未精确命中的段落检查索引状态，并按最多16条一批调用模型。 */
    private void predictSemanticBatches(List<String> normalizedParagraphs,
                                        List<Integer> semanticIndexes,
                                        List<PredictionResponse> responses,
                                        String userId) {
        IndexStatusResponse indexStatus = indexService.status();
        if ("EMPTY".equals(indexStatus.getStatus())) {
            for (Integer index : semanticIndexes) {
                responses.set(index, empty(0D, Collections.<PredictionReference>emptyList()));
            }
            return;
        }
        if ("NOT_READY".equals(indexStatus.getStatus()) || "LOAD_FAILED".equals(indexStatus.getStatus())
                || indexStatus.getSampleCount() == 0) {
            throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                    "历史段落向量索引不可用，当前状态=" + indexStatus.getStatus());
        }

        int batchSize = effectiveBatchSize();
        for (int start = 0; start < semanticIndexes.size(); start += batchSize) {
            int end = Math.min(start + batchSize, semanticIndexes.size());
            List<String> batchTexts = new ArrayList<String>(end - start);
            for (int i = start; i < end; i++) {
                batchTexts.add(normalizedParagraphs.get(semanticIndexes.get(i)));
            }
            EmbeddingBatchResult embedded = embeddingClient.embed(batchTexts, userId);
            if (embedded == null || embedded.getVectors() == null
                    || embedded.getVectors().size() != batchTexts.size()) {
                throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                        "Embedding返回数量与输入数量不一致");
            }
            for (int i = start; i < end; i++) {
                int responseIndex = semanticIndexes.get(i);
                float[] vector = embedded.getVectors().get(i - start);
                responses.set(responseIndex, predictFromVector(vector));
            }
        }
    }

    /** 使用已归一化向量执行内存检索，并复用单段预测的阈值与投票规则。 */
    private PredictionResponse predictFromVector(float[] vector) {
        List<ParagraphSearchResult> allMatches = indexService.search(vector,
                properties.getSearch().getRetrieveTopK());
        if (allMatches.isEmpty()) {
            throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                    "历史段落向量索引没有可用样本");
        }
        double maxSimilarity = allMatches.get(0).getSimilarity();
        List<ParagraphSearchResult> matches = reliableMatches(allMatches,
                properties.getSearch().getMinSimilarity());
        if (matches.isEmpty()) {
            return empty(maxSimilarity,
                    references(allMatches, properties.getSearch().getEvidenceTopK()));
        }
        return semantic(matches);
    }

    private int effectiveBatchSize() {
        return Math.min(MAX_EMBEDDING_BATCH_SIZE,
                Math.max(1, properties.getEmbedding().getBatchSize()));
    }

    /** 将 Hash 完全相同的历史样本转换为 100% 可信的精确匹配响应。 */
    private PredictionResponse exact(ParagraphVectorSample sample) {
        List<ChangeTypePrediction> types = new ArrayList<ChangeTypePrediction>();
        for (String code : sample.getChangeTypeCodes()) {
            types.add(new ChangeTypePrediction(code, 1D, 1, "HIGH"));
        }
        PredictionReference reference = new PredictionReference(sample.getSampleId(), sample.getOriginalText(),
                1D, sample.getChangeTypeCodes());
        return new PredictionResponse("EXACT", properties.getEmbedding().getModelVersion(), 1D,
                types, Collections.singletonList(reference));
    }

    /**
     * 根据相似历史段落已有的类型编码进行综合判断，并整理返回给调用方的参考段落。
     *
     * <p>召回到相似段落只代表存在可供参考的历史证据，不代表已经得到可靠的类型结果。
     * 投票未产出类型时，会继续判断第一名是否达到强匹配阈值；满足时将
     * 第一名历史段落的类型作为 {@code CANDIDATE} 返回。两种规则均无法产出类型时才返回
     * {@code NO_RELIABLE_MATCH}，同时保留最高相似度和参考段落。</p>
     */
    private PredictionResponse semantic(List<ParagraphSearchResult> matches) {
        int voteCount = Math.min(properties.getSearch().getVoteTopK(), matches.size());
        Map<String, Vote> votes = new HashMap<String, Vote>();
        double totalWeight = 0D;
        for (int i = 0; i < voteCount; i++) {
            ParagraphSearchResult match = matches.get(i);
            // 相似度越高，历史样本的影响越大。使用平方后，高相似记录会比普通相似记录更有影响力。
            double weight = match.getSimilarity() * match.getSimilarity();
            totalWeight += weight;
            for (String code : match.getSample().getChangeTypeCodes()) {
                Vote vote = votes.get(code);
                if (vote == null) {
                    vote = new Vote();
                    votes.put(code, vote);
                }
                vote.weight += weight;
                vote.support++;
            }
        }
        List<ChangeTypePrediction> types = new ArrayList<ChangeTypePrediction>();
        if (totalWeight > 0D) {
            for (Map.Entry<String, Vote> entry : votes.entrySet()) {
                // 类型得分表示：支持这个类型的历史样本权重，占全部参与判断样本权重的比例。
                double score = entry.getValue().weight / totalWeight;
                if (score < properties.getSearch().getCandidateThreshold()) {
                    continue;
                }
                boolean high = score >= properties.getSearch().getHighThreshold()
                        && entry.getValue().support >= properties.getSearch().getMinSupportCount();
                types.add(new ChangeTypePrediction(entry.getKey(), score, entry.getValue().support,
                        high ? "HIGH" : "CANDIDATE"));
            }
        }
        // 优先相信多条历史记录形成的共同结果；只有没有类型达标时，才考虑最相似的第一条记录。
        applyStrongMatchFallback(matches, votes, totalWeight, types);
        types.sort(Comparator.comparingDouble(ChangeTypePrediction::getScore).reversed()
                .thenComparing(ChangeTypePrediction::getCode));

        List<PredictionReference> references = references(matches, properties.getSearch().getEvidenceTopK());
        String matchType = types.isEmpty() ? "NO_RELIABLE_MATCH" : "SEMANTIC";
        return new PredictionResponse(matchType, properties.getEmbedding().getModelVersion(),
                matches.get(0).getSimilarity(), types, references);
    }

    /**
     * 当多样本投票没有类型达到候选阈值时，判断第一名是否达到强相似候选阈值。
     *
     * <p>兜底返回的类型一律为 {@code CANDIDATE}，即使第一名相似度超过高可信阈值也不标记
     * 为 {@code HIGH}，因为它仍然只依赖一条主要历史证据。类型得分仍使用统一的投票得分，
     * 第一名段落相似度通过响应中的 {@code maxSimilarity} 表达。</p>
     */
    private boolean applyStrongMatchFallback(List<ParagraphSearchResult> matches,
                                             Map<String, Vote> votes,
                                             double totalWeight,
                                             List<ChangeTypePrediction> types) {
        if (!types.isEmpty() || matches.isEmpty()) {
            return false;
        }

        ParagraphSearchResult first = matches.get(0);
        double firstSimilarity = first.getSimilarity();
        if (firstSimilarity < properties.getSearch().getStrongMatchThreshold()) {
            return false;
        }

        for (String code : first.getSample().getChangeTypeCodes()) {
            Vote vote = votes.get(code);
            int supportCount = vote == null ? 1 : vote.support;
            double voteScore = vote == null || totalWeight <= 0D ? 0D : vote.weight / totalWeight;
            types.add(new ChangeTypePrediction(code, voteScore, supportCount, "CANDIDATE"));
        }
        log.info("类型投票无结果，启用强相似候选兜底, sampleId={}, firstSimilarity={}, threshold={}, typeCount={}",
                first.getSample().getSampleId(), firstSimilarity,
                properties.getSearch().getStrongMatchThreshold(), types.size());
        return true;
    }

    /** 构造没有可靠类型结果的预测响应。 */
    private PredictionResponse empty(double similarity, List<PredictionReference> references) {
        return new PredictionResponse("NO_RELIABLE_MATCH",
                properties.getEmbedding().getModelVersion(), similarity,
                Collections.<ChangeTypePrediction>emptyList(), references);
    }

    /** 截取达到最低相似度的连续候选；输入列表已经按相似度倒序排列。 */
    private List<ParagraphSearchResult> reliableMatches(List<ParagraphSearchResult> matches, double threshold) {
        List<ParagraphSearchResult> result = new ArrayList<ParagraphSearchResult>();
        for (ParagraphSearchResult match : matches) {
            if (match.getSimilarity() < threshold) {
                break;
            }
            result.add(match);
        }
        return result;
    }

    /** 将内部检索结果转换成最多指定数量的历史证据段落。 */
    private List<PredictionReference> references(List<ParagraphSearchResult> matches, int limit) {
        int evidenceCount = Math.min(limit, matches.size());
        List<PredictionReference> references = new ArrayList<PredictionReference>(evidenceCount);
        for (int i = 0; i < evidenceCount; i++) {
            ParagraphSearchResult match = matches.get(i);
            references.add(new PredictionReference(match.getSample().getSampleId(),
                    match.getSample().getOriginalText(), match.getSimilarity(),
                    match.getSample().getChangeTypeCodes()));
        }
        return references;
    }

    /** 一个类型在本次判断中的临时统计，只在当前服务内部使用。 */
    private static class Vote {
        /** 支持该类型的所有历史样本权重之和。 */
        private double weight;
        /** 支持该类型的历史样本数量。 */
        private int support;
    }
}
