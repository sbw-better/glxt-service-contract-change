package com.citics.glxt.contractchange.embedding.dto;

import lombok.Data;

import java.util.List;

/** 统一模型网关响应中的单条向量数据。 */
@Data
public class EmbeddingGatewayData {
    /** OpenAI兼容响应中的输入序号，批量调用时用于还原严格的输入顺序。 */
    private Integer index;
    /** 模型返回的原始浮点向量。 */
    private List<Double> embedding;
}
