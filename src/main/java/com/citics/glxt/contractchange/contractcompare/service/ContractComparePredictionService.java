package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.BusinessTypePrediction;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.PredictionSummary;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService.LenientPrediction;
import com.citics.glxt.contractchange.service.FileChangeTypeAggregator;
import com.citics.glxt.contractchange.util.ContractTextNormalizer;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 为合同比对或变更提取结果补充历史向量库业务类型识别。 */
@Service
@Slf4j
public class ContractComparePredictionService {
    private final ContractParagraphPredictionService predictionService;
    private final ContractChangeProperties properties;
    private final FileChangeTypeAggregator changeTypeAggregator;

    public ContractComparePredictionService(ContractParagraphPredictionService predictionService,
                                            ContractChangeProperties properties,
                                            FileChangeTypeAggregator changeTypeAggregator) {
        this.predictionService = predictionService;
        this.properties = properties;
        this.changeTypeAggregator = changeTypeAggregator;
    }

    public void predict(ContractCompareResponse response, AnalysisType analysisType, String userId) {
        long started = System.currentTimeMillis();
        List<Target> targets = targets(response);
        Map<String, List<Target>> primaryGroups = new LinkedHashMap<String, List<Target>>();
        for (Target target : targets) {
            String base = ContractTextNormalizer.normalize(target.baseText);
            if (base.isEmpty()) {
                target.paragraph.setBusinessTypePrediction(failed(target.primaryScope,
                        "没有可用于类型识别的段落内容", "FAILED", false));
            } else if (base.length() > maxLength()) {
                target.paragraph.setBusinessTypePrediction(failed(target.primaryScope,
                        "段落超过类型识别字符上限", "SKIPPED_TOO_LONG", false));
            } else {
                addGroup(primaryGroups, base, target);
            }
        }
        applyPredictions(primaryGroups, userId, false);

        Map<String, List<Target>> fallbackGroups = new LinkedHashMap<String, List<Target>>();
        for (Target target : targets) {
            BusinessTypePrediction current = target.paragraph.getBusinessTypePrediction();
            if (current == null || !"NO_RELIABLE_MATCH".equals(current.getStatus())) {
                continue;
            }
            String context = boundedContext(target, analysisType);
            if (!context.isEmpty()
                    && !context.equals(ContractTextNormalizer.normalize(target.baseText))) {
                addGroup(fallbackGroups, context, target);
            }
        }
        applyPredictions(fallbackGroups, userId, true);

        int matched = 0;
        int noMatch = 0;
        int failed = 0;
        int skippedTooLong = 0;
        int fallbackMatched = 0;
        for (Target target : targets) {
            String status = target.paragraph.getBusinessTypePrediction().getStatus();
            if ("MATCHED".equals(status)) {
                matched++;
                if (target.paragraph.getBusinessTypePrediction().isFallbackUsed()) {
                    fallbackMatched++;
                }
            } else if ("NO_RELIABLE_MATCH".equals(status)) {
                noMatch++;
            } else if ("SKIPPED_TOO_LONG".equals(status)) {
                skippedTooLong++;
            } else {
                failed++;
            }
        }
        response.setPredictionSummary(new PredictionSummary(
                matched, noMatch, failed, skippedTooLong));
        response.setFileChangeTypeCodes(aggregateFileChangeTypes(targets));
        if (failed > 0) {
            ensureWarnings(response).add("有" + failed + "个变化段落未完成业务类型识别，请人工复核");
        }
        if (skippedTooLong > 0) {
            ensureWarnings(response).add("有" + skippedTooLong
                    + "个变化段落超过类型识别字符上限，未调用模型，请人工复核");
        }
        log.info("合同比对业务类型识别完成, analysisType={}, total={}, matched={}, contextFallbackMatched={}, noMatch={}, failed={}, skippedTooLong={}, elapsedMs={}",
                analysisType, targets.size(), matched, fallbackMatched, noMatch, failed,
                skippedTooLong,
                System.currentTimeMillis() - started);
    }

    private List<String> aggregateFileChangeTypes(List<Target> targets) {
        List<String> paragraphs = new ArrayList<String>();
        List<List<ChangeTypePrediction>> changeTypes =
                new ArrayList<List<ChangeTypePrediction>>();
        for (Target target : targets) {
            String normalized = ContractTextNormalizer.normalize(target.baseText);
            if (normalized.isEmpty()) {
                continue;
            }
            paragraphs.add(normalized);
            BusinessTypePrediction prediction = target.paragraph.getBusinessTypePrediction();
            if (prediction != null && "MATCHED".equals(prediction.getStatus())
                    && prediction.getChangeTypes() != null) {
                changeTypes.add(prediction.getChangeTypes());
            } else {
                changeTypes.add(Collections.<ChangeTypePrediction>emptyList());
            }
        }
        return changeTypeAggregator.aggregateChangeTypes(paragraphs, changeTypes);
    }

    private void applyPredictions(Map<String, List<Target>> groups, String userId,
                                  boolean contextFallback) {
        if (groups.isEmpty()) {
            return;
        }
        List<String> texts = new ArrayList<String>(groups.keySet());
        List<LenientPrediction> results = predictionService.predictBatchLenient(texts, userId);
        for (int index = 0; index < texts.size(); index++) {
            LenientPrediction item = results.get(index);
            for (Target target : groups.get(texts.get(index))) {
                if (!item.isSuccess()) {
                    target.paragraph.setBusinessTypePrediction(failed(
                            contextFallback ? target.contextScope : target.primaryScope,
                            predictionFailureMessage(item.getErrorCode()), "FAILED",
                            contextFallback));
                    continue;
                }
                PredictionResponse prediction = item.getPrediction();
                BusinessTypePrediction converted = convert(prediction,
                        contextFallback ? target.contextScope : target.primaryScope,
                        contextFallback);
                target.paragraph.setBusinessTypePrediction(converted);
            }
        }
    }

    private String predictionFailureMessage(String errorCode) {
        if ("INDEX_UNAVAILABLE".equals(errorCode)) {
            return "历史向量索引不可用，请检查 /service/contract-change/index/status";
        }
        if ("EMBEDDING_UNAVAILABLE".equals(errorCode)) {
            return "Embedding服务暂不可用，请检查网关地址、API Key、模型名称和网络";
        }
        if ("INVALID_INPUT".equals(errorCode)) {
            return "类型识别输入不合法";
        }
        return "历史向量类型识别服务暂不可用";
    }

    private BusinessTypePrediction convert(PredictionResponse source, String scope,
                                             boolean contextFallback) {
        BusinessTypePrediction result = new BusinessTypePrediction();
        boolean matched = source.getChangeTypes() != null && !source.getChangeTypes().isEmpty()
                && !"NO_RELIABLE_MATCH".equals(source.getMatchType());
        result.setStatus(matched ? "MATCHED" : "NO_RELIABLE_MATCH");
        result.setInputScope(scope);
        result.setFallbackUsed(contextFallback);
        result.setMatchType(source.getMatchType());
        result.setModelVersion(source.getModelVersion());
        result.setMaxSimilarity(source.getMaxSimilarity());
        result.setReferences(source.getReferences());
        if (contextFallback && source.getChangeTypes() != null) {
            List<ChangeTypePrediction> capped = new ArrayList<ChangeTypePrediction>();
            for (ChangeTypePrediction type : source.getChangeTypes()) {
                capped.add(new ChangeTypePrediction(type.getCode(), type.getScore(),
                        type.getSupportCount(), "CANDIDATE"));
            }
            result.setChangeTypes(capped);
        } else {
            result.setChangeTypes(source.getChangeTypes());
        }
        return result;
    }

    private BusinessTypePrediction failed(String scope, String message, String status,
                                          boolean fallbackUsed) {
        BusinessTypePrediction result = new BusinessTypePrediction();
        result.setStatus(status);
        result.setInputScope(scope);
        result.setFallbackUsed(fallbackUsed);
        result.setModelVersion(properties.getEmbedding().getModelVersion());
        result.setChangeTypes(Collections.<ChangeTypePrediction>emptyList());
        result.setReferences(Collections.emptyList());
        result.setMessage(message);
        return result;
    }

    private List<Target> targets(ContractCompareResponse response) {
        List<Target> result = new ArrayList<Target>();
        if (response.getChanges() == null) {
            return result;
        }
        for (ClauseChange change : response.getChanges()) {
            if (change.getChangedParagraphs() == null) {
                continue;
            }
            for (ChangedParagraph paragraph : change.getChangedParagraphs()) {
                boolean oldSide = paragraph.getParagraphChangeType() == ChangeType.DELETED;
                String text = oldSide ? paragraph.getOldContent() : paragraph.getNewContent();
                result.add(new Target(change, paragraph, text,
                        oldSide ? "OLD_PARAGRAPH" : "NEW_PARAGRAPH",
                        oldSide ? "OLD_CONTEXT" : "NEW_CONTEXT"));
            }
        }
        return result;
    }

    private String boundedContext(Target target, AnalysisType analysisType) {
        String heading = join(target.change.getClauseNo(), target.change.getClauseTitle(),
                target.change.getSourceHeading(), target.change.getTargetClauseReference());
        String full = "";
        if (target.change.getContext() != null) {
            full = "OLD_CONTEXT".equals(target.contextScope)
                    ? target.change.getContext().getOldContent()
                    : target.change.getContext().getNewContent();
        }
        if (analysisType == AnalysisType.CHANGE_DOCUMENT) {
            full = "";
        }
        return targetFocusedContext(heading, target.baseText, full);
    }

    /**
     * 上下文输入始终把条款标题和当前目标段落放在前面，再补充目标前后的正文。
     * 这样同一条款内多个变化段落不会因为共享完整条款而生成完全相同的兜底输入。
     */
    private String targetFocusedContext(String heading, String baseText, String fullText) {
        String base = ContractTextNormalizer.normalize(baseText);
        String normalizedHeading = ContractTextNormalizer.normalize(heading);
        if (base.isEmpty() || base.length() > maxLength()) {
            return "";
        }

        int headingBudget = Math.max(0, maxLength() - base.length() - 1);
        if (normalizedHeading.length() > headingBudget) {
            normalizedHeading = normalizedHeading.substring(0, headingBudget);
        }
        String required = ContractTextNormalizer.normalize(join(normalizedHeading, base));
        if (required.length() >= maxLength()) {
            return required;
        }

        String normalizedFull = ContractTextNormalizer.normalize(fullText);
        if (normalizedFull.isEmpty()) {
            return required;
        }
        int baseAt = normalizedFull.indexOf(base);
        String before;
        String after;
        if (baseAt < 0) {
            before = "";
            after = normalizedFull;
        } else {
            before = normalizedFull.substring(0, baseAt).trim();
            after = normalizedFull.substring(baseAt + base.length()).trim();
        }
        if (!normalizedHeading.isEmpty() && before.startsWith(normalizedHeading)) {
            before = before.substring(normalizedHeading.length()).trim();
        }

        int available = Math.max(0, maxLength() - required.length() - 2);
        int beforeBudget = Math.min(before.length(), available / 2);
        int afterBudget = Math.min(after.length(), available - beforeBudget);
        int remaining = available - beforeBudget - afterBudget;
        if (remaining > 0) {
            int extraBefore = Math.min(remaining, before.length() - beforeBudget);
            beforeBudget += extraBefore;
            remaining -= extraBefore;
            afterBudget += Math.min(remaining, after.length() - afterBudget);
        }
        String selectedBefore = before.substring(before.length() - beforeBudget);
        String selectedAfter = after.substring(0, afterBudget);
        String bounded = ContractTextNormalizer.normalize(
                join(normalizedHeading, base, selectedBefore, selectedAfter));
        return bounded.length() <= maxLength()
                ? bounded : bounded.substring(0, maxLength());
    }

    private String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (result.length() > 0) {
                    result.append(' ');
                }
                result.append(value.trim());
            }
        }
        return result.toString();
    }

    private int maxLength() {
        return properties.getSearch().getMaxParagraphLength();
    }

    private void addGroup(Map<String, List<Target>> groups, String text, Target target) {
        List<Target> values = groups.get(text);
        if (values == null) {
            values = new ArrayList<Target>();
            groups.put(text, values);
        }
        values.add(target);
    }

    private List<String> ensureWarnings(ContractCompareResponse response) {
        if (response.getWarnings() == null) {
            response.setWarnings(new ArrayList<String>());
        } else if (!(response.getWarnings() instanceof ArrayList)) {
            response.setWarnings(new ArrayList<String>(response.getWarnings()));
        }
        return response.getWarnings();
    }

    private static class Target {
        private final ClauseChange change;
        private final ChangedParagraph paragraph;
        private final String baseText;
        private final String primaryScope;
        private final String contextScope;

        private Target(ClauseChange change, ChangedParagraph paragraph, String baseText,
                       String primaryScope, String contextScope) {
            this.change = change;
            this.paragraph = paragraph;
            this.baseText = baseText;
            this.primaryScope = primaryScope;
            this.contextScope = contextScope;
        }
    }
}
