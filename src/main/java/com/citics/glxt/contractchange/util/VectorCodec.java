package com.citics.glxt.contractchange.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Float32 向量与 Oracle BLOB 之间的小端序编解码工具。 */
public final class VectorCodec {
    private VectorCodec() { }

    /** 将浮点向量编码为每维 4 字节的小端序数组。 */
    public static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) buffer.putFloat(value);
        return buffer.array();
    }

    /**
     * 从小端序 BLOB 数据恢复浮点向量。
     *
     * @throws IllegalArgumentException BLOB 长度与目标维度不一致时抛出
     */
    public static float[] decode(byte[] bytes, int dimension) {
        if (bytes == null || dimension <= 0 || bytes.length != dimension * 4) {
            throw new IllegalArgumentException("BLOB向量长度与维度不一致");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) vector[i] = buffer.getFloat();
        return vector;
    }
}
