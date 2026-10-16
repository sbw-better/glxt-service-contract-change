package com.citics.glxt.contractchange.util;

/** 合同段落规范化工具，导入与预测必须共用同一规则。 */
public final class ContractTextNormalizer {
    private ContractTextNormalizer() {
    }

    /**
     * 统一全角空格和换行并压缩连续空白。
     *
     * <p>不删除否定词、数字、比例、主体名称或标点，避免改变合同语义。</p>
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        // 先把 Java 识别的全部空白（包含 NBSP、窄不换行空格等）统一为 ASCII 空格。
        // Oracle ZHS16GBK CLOB 无法表示 U+00A0 等字符；若直接参与Hash，写库转换后会校验失败。
        StringBuilder spacesNormalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isWhitespace(value) || Character.isSpaceChar(value)) spacesNormalized.append(' ');
            else spacesNormalized.append(value);
        }
        return spacesNormalized.toString()
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** 将原文中的 Unicode 空格字符映射为数据库兼容的普通空格，同时保留换行和制表符。 */
    public static String replaceUnicodeSpaceCharacters(String text) {
        if (text == null) return "";
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            result.append(Character.isSpaceChar(value) ? ' ' : value);
        }
        return result.toString();
    }
}
