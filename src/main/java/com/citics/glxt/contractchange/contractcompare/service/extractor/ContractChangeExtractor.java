package com.citics.glxt.contractchange.contractcompare.service.extractor;

import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;

/** 将不同文档类型统一提取为变化段落；不负责向量预测和文档级聚合。 */
public interface ContractChangeExtractor {
    AnalysisType supportType();

    /** 校验当前文件类型自身需要的请求参数。 */
    void validateRequest(ContractCompareRequest request);

    ContractCompareResponse extract(ContractCompareRequest request);
}
