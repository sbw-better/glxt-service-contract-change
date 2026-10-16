package com.citics.glxt.contractchange.embedding.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** OpenAI 兼容 Embedding 请求体；input统一使用字符串数组，单条输入也保持相同结构。 */
@Getter
@AllArgsConstructor
public class EmbeddingGatewayRequest {
    /** 模型平台分配的请求模型名称。 */
    private final String model;
    /** 单条或批量输入均使用 List&lt;String&gt;，避免不同批大小改变请求结构。 */
    private final Object input;
    /**
     * 明确要求网关返回的向量维度。
     *
     * <p>统一网关已确认使用复数字段 {@code dimensions}，并接受字符串形式的维度值。</p>
     */
    private final String dimensions;
    /** 统一网关确认的浮点向量编码格式。 */
    @JsonProperty("encoding_format")
    private final String encodingFormat;
}
