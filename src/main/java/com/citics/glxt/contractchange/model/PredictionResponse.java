package com.citics.glxt.contractchange.model;

import com.citics.glxt.contractchange.common.FourDecimalDoubleSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 新合同段落的完整预测结果。 */
@Data
@AllArgsConstructor
public class PredictionResponse {
    /** EXACT、SEMANTIC 或 NO_RELIABLE_MATCH。 */
    private String matchType;
    /** 本次预测使用的模型版本。 */
    private String modelVersion;
    /** 召回历史样本的最高相似度；精确命中时为 1。 */
    @JsonSerialize(using = FourDecimalDoubleSerializer.class)
    private double maxSimilarity;
    /** 达到候选阈值的多标签预测结果。 */
    private List<ChangeTypePrediction> changeTypes;
    /** 按相似度倒序排列的历史参考段落。 */
    private List<PredictionReference> references;
}
