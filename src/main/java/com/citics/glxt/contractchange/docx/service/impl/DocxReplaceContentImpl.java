package com.citics.glxt.contractchange.docx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citics.glxt.contractchange.docx.domain.ReplaceContentConfigRange;
import com.citics.glxt.contractchange.docx.domain.ReplaceContentConfiguration;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.DocxCommonService;
import com.citics.glxt.contractchange.docx.service.DocxReplaceContent;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigRangeService;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.relationships.Relationships;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.xml.bind.JAXBElement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;

@Slf4j
@Service
public class DocxReplaceContentImpl implements DocxReplaceContent {

    private static final ObjectFactory factory = new ObjectFactory();

    @Resource
    private DocxCommonService docxCommonService;

    @Resource
    private ReplaceContentConfigurationService configService;

    @Resource
    private ReplaceContentConfigRangeService configRangeService;

    // 变量开始字符串-主，例如：${FXJSSSFCHTZDDZCOA}
    private static final Pattern START_MAIN_PATTERN =
            Pattern.compile("\\$\\{([A-Z0-9]+)\\}");

    // 变量结束字符串-主，例如：${/FXJSSSFCHTZDDZCOA}
    private static final Pattern END_MAIN_PATTERN =
            Pattern.compile("\\$\\{/([A-Z0-9]+)\\}");

    // 变量开始字符串-子，例如：${FXJSSSFCHTZDDZCOA-1}
    private static final Pattern START_SUB_PATTERN =
            Pattern.compile("\\$\\{([A-Z0-9]+)-(\\d+)\\}");

    // 变量结束字符串-子主，例如：${/FXJSSSFCHTZDDZCOA-1}
    private static final Pattern END_SUB_PATTERN =
            Pattern.compile("\\$\\{/([A-Z0-9]+)-(\\d+)\\}");

    // 变量字符串-全部
    private static final Pattern ALL_PATTERN =
            Pattern.compile("\\$\\{[/A-Z0-9-]+\\}");

    /**
     * 替换目标文件中的内容 主入口
     */
    @Override
    public String replaceContentMain(DocxAnalysisDTO docxAnalysisDTO) {
        String res = "";

        // 获取信息并校验
        String replaceFileGetPath = docxAnalysisDTO.getReplaceFileGetPath();
        String replaceFileSavePath = docxAnalysisDTO.getReplaceFileSavePath();
        String replaceConfigIds = docxAnalysisDTO.getReplaceConfigIds();
        String replaceConfigRangeIds = docxAnalysisDTO.getReplaceConfigRangeIds();
        Boolean isAllText = docxAnalysisDTO.getIsAllText();
        Boolean isModify = docxAnalysisDTO.getIsModify();

        isFalse(
                StringUtils.isEmpty(replaceFileGetPath),
                "文档获取地址不得为空，请核对！"
        );

        isFalse(
                StringUtils.isEmpty(replaceFileSavePath),
                "文档保存地址不得为空，请核对！"
        );

        isFalse(
                StringUtils.isEmpty(replaceConfigIds),
                "文档内容替换配置表ID串不得为空，请核对！"
        );

        isFalse(
                isAllText == null,
                "是否为纯文本替换参数不得为空，请核对！"
        );

        isFalse(
                isModify == null,
                "替换时是否开启修订模式不得为空，请核对！"
        );

        // 变量初始化
        String[] replaceConfigIdsList = {};
        List<ReplaceContentConfiguration> configList = new ArrayList<>();

        String[] replaceConfigRangeIdsList = {};
        List<ReplaceContentConfigRange> configRangeList = new ArrayList<>();

        // 获取文档内容替换配置表
        replaceConfigIdsList = replaceConfigIds.split(",");

        isFalse(
                replaceConfigIdsList.length == 0,
                "文档内容替换配置表ID串解析后长度为0，请核对！"
        );

        LambdaQueryWrapper<ReplaceContentConfiguration> lambda =
                new LambdaQueryWrapper<>();

        lambda.in(
                ReplaceContentConfiguration::getId,
                Arrays.asList(replaceConfigIdsList)
        );

        configList = configService.list(lambda);

        // 获取文档内容替换范围变量配置表
        replaceConfigRangeIdsList = replaceConfigRangeIds.split(",");

        if (replaceConfigRangeIdsList.length > 0) {
            LambdaQueryWrapper<ReplaceContentConfigRange> lambdaRange =
                    new LambdaQueryWrapper<>();

            lambdaRange.in(
                    ReplaceContentConfigRange::getId,
                    Arrays.asList(replaceConfigRangeIdsList)
            );

            configRangeList = configRangeService.list(lambdaRange);
        }

        // 如果是纯文本替换
        if (isAllText) {

            // 对获取的文档内容替换配置表列表做转换
            Map<String, Object> paramMa = new HashMap<>();
            Map<String, Object> paramRangeMap = new HashMap<>();

            if (configList.size() > 0) {
                for (ReplaceContentConfiguration m : configList) {
                    paramMa.put(
                            m.getOldContent(),
                            m.getNewContent()
                    );
                }
            }

            if (configRangeList.size() > 0) {
                for (ReplaceContentConfigRange n : configRangeList) {
                    paramRangeMap.put(
                            n.getParamName(),
                            n.getParamValue()
                    );
                }
            }

            // 替换内容
            res = replaceContent(
                    replaceFileGetPath,
                    replaceFileSavePath,
                    null,
                    paramMa,
                    paramRangeMap,
                    isAllText,
                    isModify
            );
        }

        return res;
    }

    /**
     * 替换目标文件中的内容
     *
     * @param targetFilePath     目标文件地址
     * @param targetFileSavePath 目标文件保存地址
     * @param wordMLPackage      目标文件的xml信息，若有传不为空，则不需要通过目标文件地址获取
     * @param paramMap           待替换的内容，例如：#{GLRMC}：管理人名称XXX。Object可能是字符串，也可能是一个List<Object>，其中包含字符串、P、表格
     * @param paramRangeMap      待替换的内容 范围变量，例如：FXJSSSFCHTZDDZCOA：1
     * @param isAllText          是否是纯文本替换          true|是；false|否
     * @param isModify           替换时是否开启修订模式     true|是；false|否
     */
    @Override
    public String replaceContent(
            String targetFilePath,
            String targetFileSavePath,
            WordprocessingMLPackage wordMLPackage,
            Map<String, Object> paramMap,
            Map<String, Object> paramRangeMap,
            boolean isAllText,
            boolean isModify) {

        String res = "";

        try {

            // 1、获取文件xml内容
            WordprocessingMLPackage wordMLPackageRevision = null;

            if (wordMLPackage == null) {

                isFalse(
                        StringUtils.isEmpty(targetFilePath),
                        "待替换文件获取地址不得为空，请核对！"
                );

                wordMLPackageRevision =
                        docxCommonService.getXmlDocument(targetFilePath);

            } else {

                wordMLPackageRevision = wordMLPackage;
            }

            MainDocumentPart mainDocumentPart =
                    wordMLPackageRevision.getMainDocumentPart();

            // 2、获取正文
            List<Object> mainDocumentContentRevision =
                    mainDocumentPart.getContent();

            isFalse(
                    mainDocumentContentRevision == null
                            || mainDocumentContentRevision.size() == 0,
                    "文档解析时或因格式问题导致读取为空，请核对或人工处理！"
            );

            // 2.1、获取文件迭代器
            ListIterator<Object> mainIterator =
                    mainDocumentContentRevision.listIterator();

            // 2.2、范围变量
            boolean isInRange = false;
            // 是否在可变范围内

            boolean isInKeepRange = false;
            // 是否在可变范围内，且在需保留范围内

            // 2.3、遍历正文
            while (mainIterator.hasNext()) {

                Object obj = mainIterator.next();

                // 处理段落
                if (obj instanceof P) {

                    P paragraph = (P) obj;

                    String pgText =
                            docxCommonService
                                    .extractFullParagraphText(
                                            paragraph.getContent()
                                    )
                                    .trim();

                    // 判断是否是几类可变作用域变量中的某一类
                    Matcher allMatcher =
                            ALL_PATTERN.matcher(pgText);

                    if (allMatcher.find()) {

                        Matcher startMainMatcher =
                                START_MAIN_PATTERN.matcher(pgText);

                        Matcher endMainMatcher =
                                END_MAIN_PATTERN.matcher(pgText);

                        // 变量开始/结束字符串-主，例如：${FXJSSSFCHTZDDZCOA} ${/FXJSSSFCHTZDDZCOA}
                        if (startMainMatcher.find()
                                || endMainMatcher.find()) {

                            mainIterator.remove();
                            continue;

                        } else {

                            Matcher startSubMatcher =
                                    START_SUB_PATTERN.matcher(pgText);

                            // 变量开始字符串-子，例如：${FXJSSSFCHTZDDZCOA-1}
                            if (startSubMatcher.find()) {

                                String paramName =
                                        startSubMatcher.group(1).trim();

                                String paramValue =
                                        startSubMatcher.group(2).trim();

                                isInRange = true;

                                if (paramRangeMap != null
                                        && paramRangeMap.containsKey(paramName)
                                        && paramValue.equals(
                                        String.valueOf(
                                                paramRangeMap.get(paramName)
                                        ))) {

                                    isInKeepRange = true;
                                }

                                mainIterator.remove();
                                continue;

                            } else {

                                Matcher endSubMatcher =
                                        END_SUB_PATTERN.matcher(pgText);

                                // 变量开始字符串-子，例如：${/FXJSSSFCHTZDDZCOA-1}
                                if (endSubMatcher.find()) {

                                    isInRange = false;
                                    isInKeepRange = false;

                                    mainIterator.remove();
                                    continue;
                                }
                            }
                        }

                    } else {

                        if (isInRange && !isInKeepRange) {
                            mainIterator.remove();
                            continue;
                        }
                    }

                    // 替换处理
                    replaceInP(
                            mainIterator,
                            paragraph,
                            paramMap,
                            isAllText,
                            isModify
                    );

                    // “（本页无正文XXX”前分页
                    if (pgText.startsWith("（本页无正文")) {

                        List<Object> paragraphContent =
                                paragraph.getContent();

                        boolean hasPage =
                                docxCommonService.hasPage(
                                        paragraphContent
                                );

                        // 没有分页符则插入一个
                        if (!hasPage) {

                            Br br = factory.createBr();
                            br.setType(STBrType.PAGE);

                            // 装入Run
                            R r = factory.createR();
                            r.getContent().add(br);

                            // 插入到段落第一个子元素
                            paragraphContent.add(0, r);
                        }
                    }

                } else {

                    if (isInRange && !isInKeepRange) {
                        mainIterator.remove();
                        continue;
                    }

                    // 处理表格。初始化表格列表，收集当前节点中的所有Tbl
                    List<Tbl> tblList = new ArrayList<>();

                    docxCommonService.findAllTblInNode(
                            obj,
                            tblList
                    );

                    if (!tblList.isEmpty()) {

                        for (Tbl table : tblList) {

                            List<Object> tableRows =
                                    table.getContent();

                            if (!CollectionUtils.isEmpty(tableRows)) {

                                // 遍历表格行，找到匹配关键字的行
                                for (Object tmp : tableRows) {

                                    Tr tr = tmp instanceof JAXBElement
                                            ? (Tr) (((JAXBElement<?>) tmp).getValue())
                                            : (Tr) tmp;

                                    for (Object cellObj : tr.getContent()) {

                                        Tc tc = cellObj instanceof JAXBElement
                                                ? (Tc) (((JAXBElement<?>) cellObj).getValue())
                                                : (Tc) cellObj;

                                        for (Object tcContent : tc.getContent()) {

                                            P para = tcContent instanceof JAXBElement
                                                    ? (P) (((JAXBElement<?>) tcContent).getValue())
                                                    : (P) tcContent;

                                            replaceInP(
                                                    mainIterator,
                                                    para,
                                                    paramMap,
                                                    isAllText,
                                                    isModify
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3、处理页眉
            RelationshipsPart relationshipsPart =
                    mainDocumentPart.getRelationshipsPart();

            Relationships relationships =
                    relationshipsPart.getRelationships();

            List<Relationship> relList =
                    relationships.getRelationship();

            for (Relationship rel : relList) {

                if (rel.getType().contains("header")
                        || rel.getType().contains("HEADER")) {

                    HeaderPart hp =
                            (HeaderPart) relationshipsPart.getPart(rel);

                    Hdr hdr = hp.getJaxbElement();

                    // 遍历页眉里所有段落，替换文本
                    for (Object obj : hdr.getContent()) {

                        if (obj instanceof P) {

                            P para = (P) obj;

                            replaceInP(
                                    mainIterator,
                                    para,
                                    paramMap,
                                    isAllText,
                                    isModify
                            );
                        }
                    }
                }
            }

            // 4、保存文件
            docxCommonService.saveModifiedDocument(
                    wordMLPackageRevision,
                    targetFileSavePath
            );

        } catch (Docx4JException e) {

            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return res;
    }

    /**
     * 处理P元素，替换
     *
     * paramMap中例如： CPOC：XXX
     * isAllText：是否是纯文本替换     true|是；false|否
     * isModify：替换时是否开启修订模式 true|是；false|否
     */
    @Override
    public void replaceInP(
            ListIterator<Object> mainIterator,
            P paragraph,
            Map<String, Object> paramMap,
            boolean isAllText,
            boolean isModify) {

        // 处理纯文本替换
        if (isAllText) {

            for (Map.Entry<String, Object> entry
                    : paramMap.entrySet()) {

                String key = entry.getKey();

                String value =
                        String.valueOf(entry.getValue());

                // 每次都更新一下段落文本，否则会出现前一个变量更新结果被后面的覆盖的情况
                String pgText =
                        docxCommonService
                                .extractFullParagraphText(
                                        paragraph.getContent()
                                )
                                .trim();

                if (pgText.contains(key)) {

                    docxCommonService.replaceKeyWordInPara(
                            paragraph,
                            pgText,
                            key,
                            value,
                            isModify
                    );
                }
            }

        } else {

            // 可能是文本、P、表格替换
            for (Map.Entry<String, Object> entry
                    : paramMap.entrySet()) {

                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof List) {

                    List<Object> valueList =
                            (List<Object>) value;

                    // 每次都更新一下段落文本，否则会出现前一个变量更新结果被后面的覆盖的情况
                    String pgText =
                            docxCommonService
                                    .extractFullParagraphText(
                                            paragraph.getContent()
                                    )
                                    .trim();

                    if (pgText.contains(key)) {

                        docxCommonService.replaceKeyWordInParaAnything(
                                mainIterator,
                                paragraph,
                                pgText,
                                key,
                                valueList,
                                isModify
                        );
                    }

                } else {

                    if (value instanceof String
                            || value == null) {

                        String valueStr =
                                value == null
                                        ? ""
                                        : String.valueOf(value);

                        // 每次都更新一下段落文本，否则会出现前一个变量更新结果被后面的覆盖的情况
                        String pgText =
                                docxCommonService
                                        .extractFullParagraphText(
                                                paragraph.getContent()
                                        )
                                        .trim();

                        if (pgText.contains(key)) {

                            docxCommonService.replaceKeyWordInPara(
                                    paragraph,
                                    pgText,
                                    key,
                                    valueStr,
                                    isModify
                            );
                        }
                    }
                }
            }
        }
    }
}
