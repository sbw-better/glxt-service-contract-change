package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 单个变更类型的投票结果。 */
@Data
@AllArgsConstructor
public class ChangeTypePrediction {
    /** 稳定的变更类型编码。 */
    private String code;
    /** 支持权重占全部投票样本权重的比例，范围为 0~1。 */
    private double score;
    /** Top-K 投票样本中包含该类型的样本数量。 */
    private int supportCount;
    /** 可信等级：HIGH 或 CANDIDATE。 */
    private String level;
}
