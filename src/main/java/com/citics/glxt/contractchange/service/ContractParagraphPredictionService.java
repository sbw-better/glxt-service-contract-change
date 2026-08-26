package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;
import com.citics.glxt.contractchange.common.CommonConstants;
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
 * <p>优先按规范化文本 Hash 精确命中；未命中时才调用向量模型并进行 Top-K 多标签加权投票。</p>
 */
@Slf4j
@Service
public class ContractParagraphPredictionService {
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
        long started = System.currentTimeMillis();
        String normalized = ContractTextNormalizer.normalize(paragraph);
        if (normalized.isEmpty()) throw new ContractChangeBusinessException("合同段落不能为空");
        if (normalized.length() > properties.getSearch().getMaxParagraphLength()) {
            throw new ContractChangeBusinessException(
                    "合同段落不能超过" + properties.getSearch().getMaxParagraphLength() + "字符");
        }
        String textHash = HashUtils.sha256(normalized);
        log.info("合同段落预测开始, textHash={}, normalizedLength={}", textHash, normalized.length());
        ParagraphVectorSample exact = indexService.exact(textHash);
        if (exact != null) {
            PredictionResponse response = exact(exact);
            log.info("合同段落预测精确命中, textHash={}, sampleId={}, typeCount={}, elapsedMs={}",
                    textHash, exact.getSampleId(), response.getChangeTypes().size(),
                    System.currentTimeMillis() - started);
            return response;
        }

        // 空库无需调用CPU模型；加载失败与正常空库必须向调用方表达为不同结果。
        IndexStatusResponse indexStatus = indexService.status();
        if ("EMPTY".equals(indexStatus.getStatus())) {
            log.info("合同段落预测结束：历史样本库为空, textHash={}, elapsedMs={}",
                    textHash, System.currentTimeMillis() - started);
            return empty(0D, Collections.<PredictionReference>emptyList());
        }
        if ("NOT_READY".equals(indexStatus.getStatus()) || "LOAD_FAILED".equals(indexStatus.getStatus())
                || indexStatus.getSampleCount() == 0) {
            throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                    "历史段落向量索引不可用，当前状态=" + indexStatus.getStatus());
        }

        EmbeddingBatchResult embedded = embeddingClient.embed(Collections.singletonList(normalized), userId);
        List<ParagraphSearchResult> allMatches = indexService.search(embedded.getVectors().get(0),
                properties.getSearch().getRetrieveTopK());
        if (allMatches.isEmpty()) {
            throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                    "历史段落向量索引没有可用样本");
        }
        double maxSimilarity = allMatches.get(0).getSimilarity();
        List<ParagraphSearchResult> matches = reliableMatches(allMatches,
                properties.getSearch().getMinSimilarity());
        if (matches.isEmpty()) {
            log.info("合同段落预测无可靠匹配, textHash={}, minSimilarity={}, elapsedMs={}",
                    textHash, properties.getSearch().getMinSimilarity(), System.currentTimeMillis() - started);
            return empty(maxSimilarity,
                    references(allMatches, properties.getSearch().getEvidenceTopK()));
        }
        PredictionResponse response = semantic(matches);
        if (response.getChangeTypes().isEmpty()) {
            log.info("合同段落召回参考样本但无类型达到候选阈值, textHash={}, maxSimilarity={}, "
                            + "candidateThreshold={}, referenceCount={}, matchType={}, elapsedMs={}",
                    textHash, response.getMaxSimilarity(), properties.getSearch().getCandidateThreshold(),
                    response.getReferences().size(), response.getMatchType(), System.currentTimeMillis() - started);
        } else {
            log.info("合同段落预测语义匹配完成, textHash={}, maxSimilarity={}, typeCount={}, "
                            + "referenceCount={}, matchType={}, elapsedMs={}",
                    textHash, response.getMaxSimilarity(), response.getChangeTypes().size(),
                    response.getReferences().size(), response.getMatchType(), System.currentTimeMillis() - started);
        }
        return response;
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
     * 对相似度候选执行平方加权的多标签投票，并构造证据段落。
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
            // 平方权重放大高相似样本的影响，同时不会引入额外可调参数。
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
                // 多标签样本的每个标签都获得该样本完整权重；分母为全部投票样本权重。
                double score = entry.getValue().weight / totalWeight;
                if (score < properties.getSearch().getCandidateThreshold()) continue;
                boolean high = score >= properties.getSearch().getHighThreshold()
                        && entry.getValue().support >= properties.getSearch().getMinSupportCount();
                types.add(new ChangeTypePrediction(entry.getKey(), score, entry.getValue().support,
                        high ? "HIGH" : "CANDIDATE"));
            }
        }
        // 多样本投票优先；只有投票完全无结果时才允许强单条匹配兜底，避免覆盖已有共识。
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
        if (!types.isEmpty() || matches.isEmpty()) return false;

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
            if (match.getSimilarity() < threshold) break;
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

    private static class Vote {
        /** 支持该类型的样本平方权重之和。 */
        private double weight;
        /** 支持该类型的历史样本数量。 */
        private int support;
    }
}
