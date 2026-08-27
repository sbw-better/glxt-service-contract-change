package com.citics.glxt.contractchange.util;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** 变更类型编码的校验、规范化和解析工具。 */
public final class ChangeTypeCodes {
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_TOTAL_LENGTH = 4000;

    private ChangeTypeCodes() {
    }

    /**
     * 将用户输入的中英文分隔符统一为英文分号，并进行去空、去重和字典序排序。
     *
     * @param raw Excel 中的原始类型编码
     * @return 可直接持久化的规范编码串
     */
    public static String canonicalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ContractChangeBusinessException("变更类型编码不能为空");
        }
        String[] values = raw.split("[;；,，]");
        TreeSet<String> codes = new TreeSet<String>();
        for (String value : values) {
            String code = value.trim();
            if (code.isEmpty()) {
                continue;
            }
            if (code.length() > MAX_CODE_LENGTH) {
                throw new ContractChangeBusinessException("单个变更类型编码不能超过64字符");
            }
            if (code.matches(".*\\s+.*")) {
                throw new ContractChangeBusinessException("变更类型编码不能包含空白字符: " + code);
            }
            codes.add(code);
        }
        if (codes.isEmpty()) {
            throw new ContractChangeBusinessException("变更类型编码不能为空");
        }
        String joined = join(codes);
        if (joined.length() > MAX_TOTAL_LENGTH) {
            throw new ContractChangeBusinessException("变更类型编码总长度不能超过4000字符");
        }
        return joined;
    }

    /** 将数据库中的规范编码串解析为只读列表。 */
    public static List<String> parse(String canonical) {
        if (canonical == null || canonical.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(canonical.split(";"))));
    }

    private static String join(Collection<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(value);
        }
        return result.toString();
    }
}
