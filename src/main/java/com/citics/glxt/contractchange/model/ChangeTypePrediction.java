package com.citics.glxt.contractchange.model;

import com.citics.glxt.contractchange.common.FourDecimalDoubleSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 单个变更类型的投票结果。 */
@Data
@AllArgsConstructor
public class ChangeTypePrediction {
    /** 稳定的变更类型编码。 */
    private String code;
    /** 类型支持得分：精确命中时为1，语义匹配时为该类型权重占全部投票权重的比例。 */
    @JsonSerialize(using = FourDecimalDoubleSerializer.class)
    private double score;
    /** Top-K 投票样本中包含该类型的样本数量。 */
    private int supportCount;
    /** 可信等级：HIGH 或 CANDIDATE。 */
    private String level;
}
