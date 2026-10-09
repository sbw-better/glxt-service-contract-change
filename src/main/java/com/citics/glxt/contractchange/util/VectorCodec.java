package com.citics.glxt.contractchange.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 向量和Oracle CLOB JSON数字数组之间的转换工具。
 *
 * <p>Java计算仍使用float数组。独立的Jackson配置不继承HTTP响应的小数格式化规则，
 * 不截断分量精度；编码后可还原相同的Float32值。全零向量由调用方归一化逻辑拒绝。</p>
 */
public final class VectorCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private VectorCodec() {
    }

    /** 保存完整float精度，不修改输入、不额外归一化。 */
    public static String encode(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }
        for (float value : vector) {
            requireFinite(value);
        }
        try {
            return MAPPER.writeValueAsString(vector);
        } catch (IOException ex) {
            throw new IllegalArgumentException("向量JSON编码失败", ex);
        }
    }

    /**
     * 严格解析CLOB中的单个JSON数字数组，拒绝类型强转、维度错误及尾随内容。
     *
     * @throws IllegalArgumentException JSON格式、数值或目标维度不正确时抛出
     */
    public static float[] decode(String json, int dimension) {
        if (json == null || json.trim().isEmpty() || dimension <= 0) {
            throw new IllegalArgumentException("向量JSON为空或维度无效");
        }
        try (JsonParser parser = MAPPER.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("向量JSON必须是数字数组");
            }
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                JsonToken token = parser.nextToken();
                if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
                    throw new IllegalArgumentException("向量JSON元素不是数字或数量不足");
                }
                // 直接按十进制解析float，保留负零并避免先解析double再转换的精度影响。
                float value = Float.parseFloat(parser.getText());
                requireFinite(value);
                vector[i] = value;
            }
            if (parser.nextToken() != JsonToken.END_ARRAY) {
                throw new IllegalArgumentException("向量JSON长度与维度不一致");
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("向量JSON包含尾随内容");
            }
            return vector;
        } catch (IOException | NumberFormatException ex) {
            // 不将向量内容或Jackson解析位置拼入业务错误，避免日志泄漏完整CLOB。
            throw new IllegalArgumentException("向量JSON格式或数值无效");
        }
    }

    private static void requireFinite(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("向量包含非有限数值或Float32溢出");
        }
    }
}
