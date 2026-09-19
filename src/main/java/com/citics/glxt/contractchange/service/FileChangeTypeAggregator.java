package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 将多个段落的预测结果汇总为文件级变更类型编码。 */
@Component
public class FileChangeTypeAggregator {

    /**
     * HIGH 类型直接进入汇总；只有 CANDIDATE 时，需要至少两个不同规范化段落共同支持。
     * 相同规范化文本只计算一次，避免模板段落重复放大证据。
     */
    public List<String> aggregate(List<String> normalizedParagraphs,
                                  List<PredictionResponse> predictions) {
        if (normalizedParagraphs == null || predictions == null
                || normalizedParagraphs.size() != predictions.size()) {
            throw new ContractChangeBusinessException("段落数量与预测结果数量不一致");
        }

        List<List<ChangeTypePrediction>> changeTypesByParagraph =
                new ArrayList<List<ChangeTypePrediction>>();
        for (PredictionResponse prediction : predictions) {
            if (prediction == null || "NO_RELIABLE_MATCH".equals(prediction.getMatchType())) {
                changeTypesByParagraph.add(null);
            } else {
                changeTypesByParagraph.add(prediction.getChangeTypes());
            }
        }
        return aggregateChangeTypes(normalizedParagraphs, changeTypesByParagraph);
    }

    /**
     * 汇总已经附着在变化段落上的类型结果。
     *
     * <p>相同规范化段落可能因所在条款上下文不同而得到不同候选，因此先合并该段落的全部类型，
     * 再把每个类型最多计作一次段落证据；同一段落同一类型同时出现 HIGH 和 CANDIDATE 时以
     * HIGH 为准。</p>
     */
    public List<String> aggregateChangeTypes(List<String> normalizedParagraphs,
                                             List<List<ChangeTypePrediction>> changeTypesByParagraph) {
        if (normalizedParagraphs == null || changeTypesByParagraph == null
                || normalizedParagraphs.size() != changeTypesByParagraph.size()) {
            throw new ContractChangeBusinessException("段落数量与预测结果数量不一致");
        }

        Map<String, Map<String, String>> levelsByParagraph =
                new HashMap<String, Map<String, String>>();
        for (int i = 0; i < normalizedParagraphs.size(); i++) {
            String normalized = normalizedParagraphs.get(i);
            if (normalized == null) {
                continue;
            }
            List<ChangeTypePrediction> types = changeTypesByParagraph.get(i);
            if (types == null) {
                continue;
            }
            Map<String, String> levelsByCode = levelsByParagraph.get(normalized);
            if (levelsByCode == null) {
                levelsByCode = new HashMap<String, String>();
                levelsByParagraph.put(normalized, levelsByCode);
            }
            for (ChangeTypePrediction type : types) {
                if (type == null || type.getCode() == null) {
                    continue;
                }
                String existing = levelsByCode.get(type.getCode());
                if ("HIGH".equals(type.getLevel()) || existing == null) {
                    levelsByCode.put(type.getCode(), type.getLevel());
                }
            }
        }

        Map<String, TypeEvidence> evidenceByCode = new HashMap<String, TypeEvidence>();
        for (Map<String, String> levelsByCode : levelsByParagraph.values()) {
            for (Map.Entry<String, String> type : levelsByCode.entrySet()) {
                String code = type.getKey();
                TypeEvidence evidence = evidenceByCode.get(code);
                if (evidence == null) {
                    evidence = new TypeEvidence();
                    evidenceByCode.put(code, evidence);
                }
                if ("HIGH".equals(type.getValue())) {
                    evidence.high = true;
                } else if ("CANDIDATE".equals(type.getValue())) {
                    evidence.candidateParagraphCount++;
                }
            }
        }

        TreeSet<String> result = new TreeSet<String>();
        for (Map.Entry<String, TypeEvidence> entry : evidenceByCode.entrySet()) {
            TypeEvidence evidence = entry.getValue();
            if (evidence.high || evidence.candidateParagraphCount >= 2) {
                result.add(entry.getKey());
            }
        }
        return new ArrayList<String>(result);
    }

    private static class TypeEvidence {
        private boolean high;
        private int candidateParagraphCount;
    }
}
