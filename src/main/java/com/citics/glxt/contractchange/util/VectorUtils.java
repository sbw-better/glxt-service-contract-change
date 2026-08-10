package com.citics.glxt.contractchange.util;

/** 向量合法性校验、归一化和相似度计算工具。 */
public final class VectorUtils {
    private VectorUtils() { }

    /** 计算两个同维向量的点积；对归一化向量即为余弦相似度。 */
    public static double dot(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("向量为空或维度不一致");
        }
        double result = 0D;
        for (int i = 0; i < left.length; i++) result += (double) left[i] * right[i];
        return result;
    }

    /** 原地执行 L2 归一化，并拒绝 NaN、无穷值和零向量。 */
    public static void normalize(float[] vector) {
        if (vector == null || vector.length == 0) throw new IllegalArgumentException("向量不能为空");
        double squaredNorm = 0D;
        for (float value : vector) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("向量包含非法数值");
            }
            squaredNorm += (double) value * value;
        }
        if (squaredNorm == 0D || Double.isNaN(squaredNorm) || Double.isInfinite(squaredNorm)) {
            throw new IllegalArgumentException("向量模长无效");
        }
        double norm = Math.sqrt(squaredNorm);
        for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / norm);
    }
}
