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
        predict(response, analysisType, userId, "n/a");
    }

    /** 将变化段落批量识别；主文本无可靠匹配时补充上下文兜底。 */
    public void predict(ContractCompareResponse response, AnalysisType analysisType, String userId,
                        String analysisId) {
        long started = System.currentTimeMillis();
        // 删除段落使用旧文本，其余变化使用新文本；相同规范化文本合并为一次预测请求。
        List<Target> targets = targets(response, analysisType);
        Map<String, List<Target>> primaryGroups = new LinkedHashMap<String, List<Target>>();
        for (Target target : targets) {
            String base = ContractTextNormalizer.normalize(target.baseText);
            if (base.isEmpty()) {
                log.error("段落业务类型识别输入为空, analysisId={}, paragraphId={}, clauseNo={}, reason={}",
                        analysisId, target.paragraphId, target.change.getClauseNo(),
                        "没有可用于类型识别的段落内容");
                target.paragraph.setBusinessTypePrediction(failed(target.primaryScope,
                        "没有可用于类型识别的段落内容", "FAILED", false, base, "EMPTY_INPUT"));
            } else if (base.length() > maxLength()) {
                log.error("段落业务类型识别未执行, analysisId={}, paragraphId={}, clauseNo={}, textLength={}, maxLength={}, reason={}",
                        analysisId, target.paragraphId, target.change.getClauseNo(),
                        base.length(), maxLength(), "段落超过类型识别字符上限");
                target.paragraph.setBusinessTypePrediction(failed(target.primaryScope,
                        "段落超过类型识别字符上限", "SKIPPED_TOO_LONG", false, base,
                        "INPUT_TOO_LONG"));
            } else {
                addGroup(primaryGroups, base, target);
            }
        }
        log.info("合同比对预测输入整理完成, analysisId={}, analysisType={}, targetCount={}, primaryUniqueCount={}",
                analysisId, analysisType, targets.size(), primaryGroups.size());
        applyPredictions(primaryGroups, userId, false, analysisId);

        // 主文本无可靠匹配时才使用条款标题和邻近上下文重试语义判断；兜底结果会降为候选级。
        Map<String, List<Target>> fallbackGroups = new LinkedHashMap<String, List<Target>>();
        for (Target target : targets) {
            BusinessTypePrediction current = target.paragraph.getBusinessTypePrediction();
            if (current == null || !"NO_RELIABLE_MATCH".equals(current.getStatus())) {
                continue;
            }
            String context = boundedContext(target, analysisType, analysisId);
            if (!context.isEmpty()
                    && !context.equals(ContractTextNormalizer.normalize(target.baseText))) {
                addGroup(fallbackGroups, context, target);
            }
        }
        log.info("合同比对上下文兜底整理完成, analysisId={}, fallbackUniqueCount={}",
                analysisId, fallbackGroups.size());
        applyPredictions(fallbackGroups, userId, true, analysisId);

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
        // 只有可靠匹配的段落参与文件级聚合；未匹配、失败和超长段落贡献空类型集合。
        response.setFileChangeTypeCodes(aggregateFileChangeTypes(targets));
        if (failed > 0) {
            ensureWarnings(response).add("有" + failed + "个变化段落未完成业务类型识别，请人工复核");
        }
        if (skippedTooLong > 0) {
            ensureWarnings(response).add("有" + skippedTooLong
                    + "个变化段落超过类型识别字符上限，未调用模型，请人工复核");
        }
        log.info("合同比对业务类型识别完成, analysisId={}, analysisType={}, total={}, matched={}, contextFallbackMatched={}, noMatch={}, failed={}, skippedTooLong={}, elapsedMs={}",
                analysisId, analysisType, targets.size(), matched, fallbackMatched, noMatch, failed,
                skippedTooLong,
                System.currentTimeMillis() - started);
    }

    /** 按段落投票规则汇总文件级类型；非MATCHED段落传入空投票，不能贡献类别。 */
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

    /** 批量调用预测服务，并把每个去重文本的识别结果回填到对应变化段落。 */
    private void applyPredictions(Map<String, List<Target>> groups, String userId,
                                  boolean contextFallback, String analysisId) {
        if (groups.isEmpty()) {
            return;
        }
        List<String> texts = new ArrayList<String>(groups.keySet());
        List<LenientPrediction> results = predictionService.predictBatchLenient(texts, userId);
        for (int index = 0; index < texts.size(); index++) {
            LenientPrediction item = results.get(index);
            for (Target target : groups.get(texts.get(index))) {
                if (!item.isSuccess()) {
                    log.error("段落业务类型识别调用失败, analysisId={}, paragraphId={}, clauseNo={}, errorCode={}, reason={}",
                            analysisId, target.paragraphId, target.change.getClauseNo(),
                            item.getErrorCode(), predictionFailureMessage(item.getErrorCode()));
                    target.paragraph.setBusinessTypePrediction(failed(
                            contextFallback ? target.contextScope : target.primaryScope,
                            predictionFailureMessage(item.getErrorCode()), "FAILED",
                            contextFallback, texts.get(index), item.getErrorCode()));
                    continue;
                }
                PredictionResponse prediction = item.getPrediction();
                BusinessTypePrediction converted = convert(prediction,
                        contextFallback ? target.contextScope : target.primaryScope,
                        contextFallback, texts.get(index));
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
        if ("EMBEDDING_QUOTA_EXCEEDED".equals(errorCode)) {
            return "Embedding模型额度已超限，请检查模型平台额度或计费状态后重试";
        }
        if ("INVALID_INPUT".equals(errorCode)) {
            return "类型识别输入不合法";
        }
        return "历史向量类型识别服务暂不可用";
    }

    private BusinessTypePrediction convert(PredictionResponse source, String scope,
                                             boolean contextFallback, String predictionText) {
        BusinessTypePrediction result = new BusinessTypePrediction();
        boolean matched = source.getChangeTypes() != null && !source.getChangeTypes().isEmpty()
                && !"NO_RELIABLE_MATCH".equals(source.getMatchType());
        result.setStatus(matched ? "MATCHED" : "NO_RELIABLE_MATCH");
        result.setInputScope(scope);
        result.setFallbackUsed(contextFallback);
        // 目标边界标记只用于Embedding输入，不属于合同文本，不回显给调用方。
        result.setPredictionText(contextFallback
                ? removeTargetBoundaryMarker(predictionText) : predictionText);
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
                                           boolean fallbackUsed, String predictionText,
                                           String errorCode) {
        BusinessTypePrediction result = new BusinessTypePrediction();
        result.setStatus(status);
        result.setInputScope(scope);
        result.setFallbackUsed(fallbackUsed);
        result.setPredictionText(fallbackUsed
                ? removeTargetBoundaryMarker(predictionText) : predictionText);
        result.setModelVersion(properties.getEmbedding().getModelVersion());
        result.setChangeTypes(Collections.<ChangeTypePrediction>emptyList());
        result.setReferences(Collections.emptyList());
        result.setMessage(message);
        result.setErrorCode(errorCode);
        return result;
    }

    /** 将条款差异展开为预测目标，并按增删方向选用新文本或旧文本。 */
    private List<Target> targets(ContractCompareResponse response, AnalysisType analysisType) {
        List<Target> result = new ArrayList<Target>();
        if (response.getChanges() == null) {
            return result;
        }
        for (ClauseChange change : response.getChanges()) {
            if (change == null) continue;
            if (change.getChangedParagraphs() == null) {
                continue;
            }
            for (ChangedParagraph paragraph : change.getChangedParagraphs()) {
                if (paragraph == null) continue;
                boolean oldSide = paragraph.getParagraphChangeType() == ChangeType.DELETED;
                String text = oldSide ? paragraph.getOldContent() : paragraph.getNewContent();
                if (analysisType == AnalysisType.CHANGE_DOCUMENT) {
                    text = stripChangeBoundaryQuotes(text);
                }
                result.add(new Target(change, paragraph, text,
                        analysisType.name() + "-" + (result.size() + 1),
                        oldSide ? "OLD_PARAGRAPH" : "NEW_PARAGRAPH",
                        oldSide ? "OLD_CONTEXT" : "NEW_CONTEXT"));
            }
        }
        return result;
    }

    /**
     * 变更函提取时，成对包住整段的双引号是变更内容边界标记，不应影响内容匹配。
     * 只去掉首尾匹配的一对引号；正文内部引号以及非变更函文本保持原样。
     */
    private String stripChangeBoundaryQuotes(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.length() < 2) {
            return text;
        }
        char opening = value.charAt(0);
        char closing = value.charAt(value.length() - 1);
        boolean paired = (opening == '“' && closing == '”')
                || (opening == '"' && closing == '"')
                || (opening == '＂' && closing == '＂');
        return paired ? value.substring(1, value.length() - 1).trim() : text;
    }

    private String boundedContext(Target target, AnalysisType analysisType, String analysisId) {
        String heading;
        if (analysisType == AnalysisType.CHANGE_DOCUMENT) {
            // 变更函标题字段常常彼此重复，优先使用目标条款引用作为唯一标题。
            heading = ContractTextNormalizer.normalize(target.change.getTargetClauseReference());
            if (heading.isEmpty()) {
                heading = join(target.change.getClauseNo(), target.change.getClauseTitle());
            }
        } else {
            String clauseNo = target.change.getClauseNo();
            String normalizedBase = ContractTextNormalizer.normalize(target.baseText);
            if (hasLeadingClauseNo(normalizedBase, clauseNo)) {
                // Aspose/差异引擎可能已把编号保留在目标段落正文里；标题再拼一次会污染兜底输入。
                log.debug("双版本上下文兜底省略正文已有的条款编号, analysisId={}, paragraphId={}, clauseNo={}, baseLength={}",
                        analysisId, target.paragraphId, clauseNo, normalizedBase.length());
                clauseNo = null;
            }
            String clauseTitle = target.change.getClauseTitle();
            if (hasLeadingClauseTitle(normalizedBase, target.change.getClauseNo(), clauseTitle)) {
                // 编号后紧跟标题时，目标正文已经包含完整标题；只省编号会留下“标题 + 原段落”的重复。
                log.debug("双版本上下文兜底省略正文已有的完整条款标题, analysisId={}, paragraphId={}, clauseNo={}, clauseTitle={}",
                        analysisId, target.paragraphId, target.change.getClauseNo(), clauseTitle);
                clauseNo = null;
                clauseTitle = null;
            }
            heading = join(clauseNo, clauseTitle,
                    target.change.getSourceHeading(), target.change.getTargetClauseReference());
        }
        String full = "";
        if (!target.change.isSynthetic() && target.change.getContext() != null) {
            full = "OLD_CONTEXT".equals(target.contextScope)
                    ? target.change.getContext().getOldContent()
                    : target.change.getContext().getNewContent();
        }
        if (analysisType == AnalysisType.CHANGE_DOCUMENT || target.change.isSynthetic()) {
            full = "";
            if (target.change.isSynthetic()) {
                // 封面、承诺书和签署区没有稳定的条款语义，避免把整块前置内容当作目标段落上下文。
                heading = "";
            }
        }
        String result = targetFocusedContext(heading, target.baseText, full);
        log.debug("合同比对上下文已裁剪, analysisId={}, paragraphId={}, synthetic={}, baseLength={}, sourceContextLength={}, outputLength={}",
                analysisId, target.paragraphId, target.change.isSynthetic(),
                ContractTextNormalizer.normalize(target.baseText).length(),
                ContractTextNormalizer.normalize(full).length(), result.length());
        return result;
    }

    /** 只有编号在目标文本中构成独立前缀时才视为重复，避免把“第二条款”等正文误判为编号。 */
    private boolean hasLeadingClauseNo(String normalizedBase, String clauseNo) {
        String normalizedClauseNo = ContractTextNormalizer.normalize(clauseNo);
        if (normalizedBase.isEmpty() || normalizedClauseNo.isEmpty()
                || !normalizedBase.startsWith(normalizedClauseNo)) {
            return false;
        }
        if (normalizedBase.length() == normalizedClauseNo.length()) {
            return true;
        }
        char next = normalizedBase.charAt(normalizedClauseNo.length());
        char last = normalizedClauseNo.charAt(normalizedClauseNo.length() - 1);
        return Character.isWhitespace(next) || "、，,：:；;。．.）)]】".indexOf(next) >= 0
                || "）)]】、，,：:；;。．.".indexOf(last) >= 0;
    }

    /** 检查目标正文是否已经以“编号 + 分隔符 + 标题”开头，防止上下文重复拼标题。 */
    private boolean hasLeadingClauseTitle(String normalizedBase, String clauseNo, String clauseTitle) {
        String number = ContractTextNormalizer.normalize(clauseNo);
        String title = ContractTextNormalizer.normalize(clauseTitle);
        if (normalizedBase.isEmpty() || number.isEmpty() || title.isEmpty()
                || !normalizedBase.startsWith(number)) {
            return false;
        }
        int index = number.length();
        while (index < normalizedBase.length()) {
            char value = normalizedBase.charAt(index);
            if (Character.isWhitespace(value) || Character.isSpaceChar(value)
                    || "、，,：:；;。．.）)]】".indexOf(value) >= 0) {
                index++;
            } else {
                break;
            }
        }
        return index + title.length() <= normalizedBase.length()
                && normalizedBase.regionMatches(true, index, title, 0, title.length());
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
        int headingPrefixEnd = matchingPrefixEndIgnoringWhitespace(before, normalizedHeading);
        if (headingPrefixEnd >= 0) {
            before = before.substring(headingPrefixEnd).trim();
        }

        String targetEndMarker = "【当前变化段落结束】";
        int available = Math.max(0, maxLength() - required.length() - targetEndMarker.length() - 2);
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
        // 按原文顺序排列，并标记目标段落终点；否则同条款多个变化会折叠成相同输入，且前后文难辨。
        String bounded = ContractTextNormalizer.normalize(
                join(normalizedHeading, selectedBefore, base + targetEndMarker, selectedAfter));
        return bounded.length() <= maxLength()
                ? bounded : bounded.substring(0, maxLength());
    }

    private String removeTargetBoundaryMarker(String text) {
        return text == null ? null : text.replace("【当前变化段落结束】", "").trim();
    }

    /** 返回与标题等价的前缀在原字符串中的结束位置，比较时忽略空格但不丢失原文字符位置。 */
    private int matchingPrefixEndIgnoringWhitespace(String source, String expectedPrefix) {
        if (source == null || expectedPrefix == null || expectedPrefix.isEmpty()) {
            return -1;
        }
        String expected = ContractCompareText.normalize(expectedPrefix);
        int expectedIndex = 0;
        int sourceIndex = 0;
        while (sourceIndex < source.length() && expectedIndex < expected.length()) {
            char current = source.charAt(sourceIndex++);
            if (Character.isWhitespace(current) || Character.isSpaceChar(current)) {
                continue;
            }
            if (Character.toLowerCase(current) != expected.charAt(expectedIndex++)) {
                return -1;
            }
        }
        while (sourceIndex < source.length()
                && (Character.isWhitespace(source.charAt(sourceIndex))
                || Character.isSpaceChar(source.charAt(sourceIndex)))) {
            sourceIndex++;
        }
        return expectedIndex == expected.length() ? sourceIndex : -1;
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
        private final String paragraphId;

        private Target(ClauseChange change, ChangedParagraph paragraph, String baseText,
                       String paragraphId,
                       String primaryScope, String contextScope) {
            this.change = change;
            this.paragraph = paragraph;
            this.baseText = baseText;
            this.primaryScope = primaryScope;
            this.contextScope = contextScope;
            this.paragraphId = paragraphId;
        }
    }
}
