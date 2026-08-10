package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 一次模型批量调用的标准化结果。 */
@Data
@AllArgsConstructor
public class EmbeddingBatchResult {
    /** 单条向量维度。 */
    private int dimension;
    /** 与请求文本顺序一致且已 L2 归一化的向量列表。 */
    private List<float[]> vectors;
}
