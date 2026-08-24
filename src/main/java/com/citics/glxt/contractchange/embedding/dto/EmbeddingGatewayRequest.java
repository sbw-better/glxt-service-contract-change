package com.citics.glxt.contractchange.embedding.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** OpenAI 兼容 Embedding 请求体；input 可以是单个字符串或字符串数组。 */
@Getter
@AllArgsConstructor
public class EmbeddingGatewayRequest {
    /** 模型平台分配的请求模型名称。 */
    private final String model;
    /** 单条调用使用 String，批量调用使用 List&lt;String&gt;。 */
    private final Object input;
}
