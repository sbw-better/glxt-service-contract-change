package com.citics.glxt.contractchange.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 向量和Oracle BLOB之间的转换工具。
 *
 * <p>Java计算时使用float数组，Oracle中使用BLOB保存。每个float固定占4字节，编码和解码使用
 * 相同的字节顺序，才能保证服务重启后读出的数字与入库前完全一致。</p>
 */
public final class VectorCodec {
    private VectorCodec() {
    }

    /** 把float数组转换成可以写入BLOB的字节数组，每个数字占4字节。 */
    public static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    /**
     * 把数据库读取的BLOB字节恢复成float数组。
     *
     * @throws IllegalArgumentException BLOB 长度与目标维度不一致时抛出
     */
    public static float[] decode(byte[] bytes, int dimension) {
        if (bytes == null || dimension <= 0 || bytes.length != dimension * 4) {
            throw new IllegalArgumentException("BLOB向量长度与维度不一致");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
