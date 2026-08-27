package com.citics.glxt.contractchange.util;

/** 向量数值检查、统一长度和相似度计算工具。 */
public final class VectorUtils {
    private VectorUtils() {
    }

    /**
     * 将两个向量对应位置的数字相乘后求和。两个向量都归一化后，这个结果就是余弦相似度。
     */
    public static double dot(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("向量为空或维度不一致");
        }
        double result = 0D;
        for (int i = 0; i < left.length; i++) {
            result += (double) left[i] * right[i];
        }
        return result;
    }

    /**
     * 把向量按相同比例缩放到统一长度，同时保持原来的方向不变。
     * 这样比较的是段落含义方向，而不是数值本身的大小。异常数字和全零向量不能用于比较，会直接拒绝。
     */
    public static void normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }
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
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
