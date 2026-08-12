package com.citics.glxt.contractchange.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 将接口中的相似度和得分序列化为最多四位小数的 JSON 数字。
 *
 * <p>该处理只发生在响应写出阶段，不修改业务对象中的原始 {@code double}，因此不会影响
 * Top-K排序、阈值判断、类型投票或强匹配兜底。使用数值而不是字符串输出，调用方仍可直接
 * 执行数值比较和百分比格式化。</p>
 */
public class FourDecimalDoubleSerializer extends JsonSerializer<Double> {
    private static final int SCALE = 4;

    @Override
    public void serialize(Double value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        double rounded = BigDecimal.valueOf(value)
                .setScale(SCALE, RoundingMode.HALF_UP)
                .doubleValue();
        generator.writeNumber(rounded);
    }
}
