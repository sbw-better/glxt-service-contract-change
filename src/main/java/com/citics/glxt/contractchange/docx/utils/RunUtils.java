package com.citics.glxt.contractchange.docx.utils;

import org.apache.commons.lang3.StringUtils;
import org.docx4j.wml.R;

import java.util.List;

public class RunUtils {

    /**
     * 获取Run元素中的文本内容
     */
    public static String getText(R r) {
        if (r == null) {
            return "";
        }

        List<Object> content = r.getContent();
        StringBuilder fullText = new StringBuilder();
        CommonUtils.extractTextFromNodeList(content, fullText);
        return StringUtils.trim(fullText.toString());
    }
}