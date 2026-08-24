package com.citics.glxt.contractchange.embedding.dto;

import lombok.Data;

import java.util.List;

/** OpenAI 兼容 Embedding 响应体，只映射本项目实际使用的 data 字段。 */
@Data
public class EmbeddingGatewayResponse {
    /** 与请求输入顺序对应的向量结果。 */
    private List<EmbeddingGatewayData> data;
}
