package com.citics.glxt.contractchange.service;

import com.citics.glxt.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import io.micrometer.core.instrument.util.StringUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.RPr;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_ATTACHMENT;
import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_MAIN;
import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_OTHER;
import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_RISK;
import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_SPECIAL;
import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_UNDERTAKING;
import static com.citics.glxt.contractchange.constants.Constants.MAX_TITLE_LEVEL;

/**
 * 按已上线文档解析流程的章节与标题规则，只读提取可参与文件级预测的顶层段落。
 */
@Service
public class ContractDocumentParagraphReader {
    private final DocxCommonService docxCommonService;

    public ContractDocumentParagraphReader(DocxCommonService docxCommonService) {
        this.docxCommonService = docxCommonService;
    }

    public List<String> readPredictionParagraphs(DocxAnalysisDTO request) {
        if (request == null || StringUtils.isEmpty(request.getFileGetPath())) {
            throw new ContractChangeBusinessException("文档获取地址不得为空，请核对！");
        }
        WordprocessingMLPackage wordPackage = docxCommonService.getXmlDocument(request.getFileGetPath());
        if (wordPackage == null || wordPackage.getMainDocumentPart() == null) {
            throw new ContractChangeBusinessException("文档解析失败，请核对文件格式！");
        }
        MainDocumentPart mainDocumentPart = wordPackage.getMainDocumentPart();
        List<Object> content = mainDocumentPart.getContent();
        if (content == null || content.isEmpty()) {
            throw new ContractChangeBusinessException("文档解析时或因格式问题读取为空，请手工处理！");
        }

        NumberingDefinitionsPart numberingPart = mainDocumentPart.getNumberingDefinitionsPart();
        Numbering numbering = numberingPart == null ? null : numberingPart.getJaxbElement();
        long currentModuleId = CONTRACT_ANALYSIS_MODULE_OTHER;
        Map<Integer, String> titleMap = initTitleMap();
        List<String> result = new ArrayList<String>();
        ListIterator<Object> iterator = content.listIterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            if (!(value instanceof P)) {
                continue;
            }
            P paragraph = (P) value;
            PPr paragraphProperties = paragraph.getPPr();
            RPr runProperties = docxCommonService.getFirstRunPrP(paragraph);
            List<String> paragraphTexts = docxCommonService.extractFullParagraphTextList(paragraph.getContent());
            for (int index = 0; index < paragraphTexts.size(); index++) {
                String paragraphText = paragraphTexts.get(index);
                if (StringUtils.isEmpty(paragraphText)) {
                    continue;
                }
                currentModuleId = docxCommonService.getCurrentModule(paragraphText, currentModuleId);
                docxCommonService.updateTextTitleLevelMap(paragraphText, titleMap, null);
                // 文档可以声明编号定义，但普通段落本身并不一定带有 NumPr。
                if (index == 0 && paragraphProperties != null && paragraphProperties.getNumPr() != null
                        && numbering != null) {
                    Map<String, String> titleModeMap = docxCommonService.getTitleModeFromP(numbering,
                            paragraphProperties);
                    paragraphText = docxCommonService.handleTitleMode(paragraph, paragraphProperties,
                            runProperties, paragraphText, titleModeMap, titleMap, true);
                    docxCommonService.updateTextTitleLevelMap(paragraphText, titleMap, null);
                }
                int titleLevel = docxCommonService.getCurrentValidTitleLevel(titleMap);
                String titlePrefix = docxCommonService.extractPrefixByLevel(paragraphText, titleLevel);
                boolean isTitle = titleLevel > 0 && !StringUtils.isEmpty(titlePrefix);
                if (isPredictionModule(currentModuleId) && !isTitle && !isModuleHeading(paragraphText)) {
                    result.add(paragraphText);
                }
            }
        }
        return result;
    }

    private boolean isPredictionModule(long moduleId) {
        return moduleId == CONTRACT_ANALYSIS_MODULE_SPECIAL
                || moduleId == CONTRACT_ANALYSIS_MODULE_RISK
                || moduleId == CONTRACT_ANALYSIS_MODULE_UNDERTAKING
                || moduleId == CONTRACT_ANALYSIS_MODULE_MAIN
                || moduleId == CONTRACT_ANALYSIS_MODULE_ATTACHMENT;
    }

    /** 章节分界文本只用于切换模块，不能作为合同业务段落参与预测。 */
    private boolean isModuleHeading(String text) {
        return "合同封面".equals(text)
                || "特别约定".equals(text)
                || "风险揭示书".equals(text)
                || "合格投资者承诺书".equals(text)
                || "目录".equals(text)
                || "一、合同当事人".equals(text)
                || "签署页".equals(text)
                || "附件".equals(text);
    }

    private Map<Integer, String> initTitleMap() {
        Map<Integer, String> titleMap = new HashMap<Integer, String>();
        for (int level = 1; level <= MAX_TITLE_LEVEL; level++) {
            titleMap.put(level, "");
        }
        return titleMap;
    }
}
