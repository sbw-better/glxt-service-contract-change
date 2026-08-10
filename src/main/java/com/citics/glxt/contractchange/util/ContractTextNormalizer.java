package com.citics.glxt.contractchange.util;

/** 合同段落规范化工具，导入与预测必须共用同一规则。 */
public final class ContractTextNormalizer {
    private ContractTextNormalizer() { }

    /**
     * 统一全角空格和换行并压缩连续空白。
     *
     * <p>不删除否定词、数字、比例、主体名称或标点，避免改变合同语义。</p>
     */
    public static String normalize(String text) {
        if (text == null) return "";
        return text.replace('\u3000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
                .replaceAll("\\s+", " ");
    }
}
