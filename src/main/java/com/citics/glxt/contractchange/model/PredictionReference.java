package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 支撑预测结果的一条历史段落证据。 */
@Data
@AllArgsConstructor
public class PredictionReference {
    /** 历史样本主键。 */
    private long sampleId;
    /** 历史样本原始段落，仅在预测响应中按业务需要返回。 */
    private String paragraph;
    /** 与新段落的余弦相似度。 */
    private double similarity;
    /** 该历史样本关联的变更类型编码。 */
    private List<String> changeTypeCodes;
}
