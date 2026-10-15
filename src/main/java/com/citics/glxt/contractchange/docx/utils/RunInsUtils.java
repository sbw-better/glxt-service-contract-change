package com.citics.glxt.contractchange.docx.utils;

import org.apache.commons.lang3.StringUtils;
import org.docx4j.wml.RunIns;

import java.util.List;

public class RunInsUtils {

    /**
     * 获取RunIns元素中的文本内容
     */
    public static String getText(RunIns ins) {
        if (ins == null) {
            return "";
        }

        List<Object> content = ins.getCustomXmlOrSmartTagOrSdt();
        StringBuilder fullText = new StringBuilder();
        CommonUtils.extractTextFromNodeList(content, fullText);
        return StringUtils.trim(fullText.toString());
    }
}
