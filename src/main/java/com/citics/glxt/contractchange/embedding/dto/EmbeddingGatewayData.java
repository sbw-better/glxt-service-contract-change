package com.citics.glxt.contractchange.embedding.dto;

import lombok.Data;

import java.util.List;

/** 统一模型网关响应中的单条向量数据。 */
@Data
public class EmbeddingGatewayData {
    /** 模型返回的原始浮点向量。 */
    private List<Double> embedding;
}
