package com.citics.glxt.contractchange.service;

import com.citics.glxt.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Map<String, TypeEvidence> evidenceByCode = new HashMap<String, TypeEvidence>();
        Set<String> countedParagraphs = new HashSet<String>();
        for (int i = 0; i < normalizedParagraphs.size(); i++) {
            String normalized = normalizedParagraphs.get(i);
            if (normalized == null || !countedParagraphs.add(normalized)) {
                continue;
            }
            PredictionResponse prediction = predictions.get(i);
            if (prediction == null || "NO_RELIABLE_MATCH".equals(prediction.getMatchType())
                    || prediction.getChangeTypes() == null) {
                continue;
            }
            Set<String> countedCodes = new HashSet<String>();
            for (ChangeTypePrediction type : prediction.getChangeTypes()) {
                if (type == null || type.getCode() == null || !countedCodes.add(type.getCode())) {
                    continue;
                }
                TypeEvidence evidence = evidenceByCode.get(type.getCode());
                if (evidence == null) {
                    evidence = new TypeEvidence();
                    evidenceByCode.put(type.getCode(), evidence);
                }
                if ("HIGH".equals(type.getLevel())) {
                    evidence.high = true;
                } else if ("CANDIDATE".equals(type.getLevel())) {
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
