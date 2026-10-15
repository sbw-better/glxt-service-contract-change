package com.citics.glxt.contractchange.docx.utils;

import org.apache.commons.lang3.StringUtils;
import org.docx4j.wml.R;
import org.docx4j.wml.RunIns;
import org.docx4j.wml.SdtElement;
import org.springframework.util.CollectionUtils;

import javax.xml.bind.JAXBElement;
import org.docx4j.wml.Text;
import java.util.Collections;
import java.util.List;

public class CommonUtils {

    /**
     * 递归获取纯文本
     */
    public static void extractTextFromNodeList(List<?> nodeList, StringBuilder fullText) {
        if (CollectionUtils.isEmpty(nodeList)) {
            return;
        }

        for (Object node : nodeList) {
            if (node instanceof Text) {
                String textValue = ((Text) node).getValue();
                if (StringUtils.isNotEmpty(textValue)) {
                    fullText.append(textValue);
                }
            } else if (node instanceof JAXBElement) {
                extractTextFromNodeList(
                        Collections.singletonList(((JAXBElement<?>) node).getValue()),
                        fullText
                );
            } else if (node instanceof R) {
                extractTextFromNodeList(((R) node).getContent(), fullText);
            } else if (node instanceof RunIns) {
                extractTextFromNodeList(
                        ((RunIns) node).getCustomXmlOrSmartTagOrSdt(),
                        fullText
                );
            } else if (node instanceof SdtElement) {
                extractTextFromNodeList(
                        ((SdtElement) node).getSdtContent().getContent(),
                        fullText
                );
            }
        }
    }
}
