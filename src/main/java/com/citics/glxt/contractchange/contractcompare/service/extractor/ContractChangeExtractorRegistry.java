package com.citics.glxt.contractchange.contractcompare.service.extractor;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 根据 AnalysisType 路由到对应变化段落提取器。 */
@Component
public class ContractChangeExtractorRegistry {
    private final Map<AnalysisType, ContractChangeExtractor> extractors =
            new EnumMap<AnalysisType, ContractChangeExtractor>(AnalysisType.class);

    public ContractChangeExtractorRegistry(List<ContractChangeExtractor> candidates) {
        if (candidates != null) {
            for (ContractChangeExtractor candidate : candidates) {
                AnalysisType type = candidate.supportType();
                if (type == null) {
                    throw new IllegalStateException("ContractChangeExtractor supportType不能为空");
                }
                if (extractors.containsKey(type)) {
                    throw new IllegalStateException("重复的合同变化提取器类型: " + type);
                }
                extractors.put(type, candidate);
            }
        }
    }

    public ContractChangeExtractor get(AnalysisType type) {
        if (type == null) {
            throw new ContractChangeBusinessException("分析类型不能为空");
        }
        ContractChangeExtractor extractor = extractors.get(type);
        if (extractor == null) {
            throw new ContractChangeBusinessException("不支持的分析类型: " + type);
        }
        return extractor;
    }
}
