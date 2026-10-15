package com.citics.glxt.contractchange.docx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citics.glxt.contractchange.docx.domain.ReplaceContentConfiguration;
import com.citics.glxt.contractchange.docx.domain.entity.CommentRange;
import com.citics.glxt.contractchange.docx.domain.entity.EnhanceObj;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.DocxCommonService;
import com.citics.glxt.contractchange.docx.service.DocxGenerateByRevision;
import com.citics.glxt.contractchange.docx.service.DocxReplaceContent;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.xml.bind.JAXBElement;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;
import static com.citics.glxt.contractchange.docx.constants.Constants.*;

@Slf4j
@Service
public class DocxGenerateByRevisionImpl implements DocxGenerateByRevision {

    @Resource
    private DocxCommonService docxCommonService;

    @Resource
    private DocxReplaceContent docxReplaceContent;

    @Resource
    private ReplaceContentConfigurationService configService;

    // docx4j对象工厂
    private static final ObjectFactory factory = new ObjectFactory();

    /**
     * 根据修订文件生成补充协议文件
     *
     * @param docxAnalysisDTO 入参
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateByRevision(DocxAnalysisDTO docxAnalysisDTO) {
        String res = "";

        // 获取基础参数并校验
        String revisionFileGetPath = docxAnalysisDTO.getRevisionFileGetPath();
        String supplementFileGetPath = docxAnalysisDTO.getSupplementFileGetPath();
        String supplementFileSavePath = docxAnalysisDTO.getSupplementFileSavePath();
        String replaceConfigIds = docxAnalysisDTO.getReplaceConfigIds();
        Boolean isAllText = docxAnalysisDTO.getIsAllText();
        Boolean isModify = docxAnalysisDTO.getIsModify();
        Integer generateByRevisionType = docxAnalysisDTO.getGenerateByRevisionType();

        isFalse(
                StringUtils.isEmpty(revisionFileGetPath),
                "修订记录文件获取地址不得为空，请核对！"
        );
        isFalse(
                StringUtils.isEmpty(supplementFileGetPath),
                "补充协议文件获取地址不得为空，请核对！"
        );
        isFalse(
                StringUtils.isEmpty(supplementFileSavePath),
                "补充协议文件保存地址不得为空，请核对！"
        );
        isFalse(
                generateByRevisionType == null,
                "生成类型不得为空，请核对！"
        );

        // 核心逻辑
        try {

            // 从文档库加载文件，包括修订版的合同、补充协议模板
            WordprocessingMLPackage xmlLoadResRevision =
                    docxCommonService.getXmlDocument(revisionFileGetPath);

            WordprocessingMLPackage xmlLoadResSupple =
                    docxCommonService.getXmlDocument(supplementFileGetPath);

            // 各类变量
            List<String> emptyCommentIds =
                    getAllEmptyComments(xmlLoadResRevision); // 所有空批注ID集合

            List<Object> mainDocumentContentRevision =
                    xmlLoadResRevision.getMainDocumentPart().getContent();

            isFalse(
                    mainDocumentContentRevision == null
                            || mainDocumentContentRevision.size() == 0,
                    "文档解析时或因格式问题导致读取为空，请核对或人工处理！"
            );

            ListIterator<Object> mainIterator =
                    mainDocumentContentRevision.listIterator();

            NumberingDefinitionsPart ndp =
                    xmlLoadResRevision.getMainDocumentPart().getNumberingDefinitionsPart();

            Numbering numbering =
                    ndp == null ? null : ndp.getJaxbElement();
            // 若ndp为空，则说明文档中全部为纯文本，无word自带序号

            long textCurrentModuleId = CONTRACT_ANALYSIS_MODULE_OTHER;
            // 当前所属模块

            Map<Integer, String> textCurrentTitleMapNum =
                    initTextTitleMap();
            // 文本标题层级，记录的是标题数字，例如：1：一、 2：（二）

            Map<Integer, String> textCurrentTitleMapText =
                    initTextTitleMap();
            // 文本标题层级，记录的是标题文本，例如：1：合同当事人 2：XXX

            List<CommentRange> commentRangeList =
                    new LinkedList<>();
            // 空批注范围结果集，保证元素插入顺序

            int commentRangeListIndex = 0;

            boolean findCommentOver = true;
            // 是否找到当前空批注的结束位置

            List<P> pList = new ArrayList<>();
            // 记录P文本

            String pgText = "";
            String pgTextBefore = "";

            PPr ppr = null;
            RPr rpr = null;

            // 获取文档内容替换配置表
            String[] replaceConfigIdsList = {};

            List<ReplaceContentConfiguration> configList =
                    new ArrayList<>();

            replaceConfigIdsList = replaceConfigIds.split(",");

            if (replaceConfigIdsList.length > 0) {

                LambdaQueryWrapper<ReplaceContentConfiguration> lambda =
                        new LambdaQueryWrapper<>();

                lambda.in(
                        ReplaceContentConfiguration::getId,
                        Arrays.asList(replaceConfigIdsList)
                );

                configList = configService.list(lambda);
            }

            // 6、遍历文档，收集有空批注的修订段落以及修订表格
            while (mainIterator.hasNext()) {

                Object obj = mainIterator.next();

                // 6.1、处理段落
                if (obj instanceof P) {

                    P paragraph = (P) obj;

                    List<Object> pContent = paragraph.getContent();

                    ppr = paragraph.getPPr();

                    rpr = docxCommonService.getFirstRunPrP(paragraph);

                    pList = docxCommonService.splitParagraphByLineBreaks(paragraph);

                    // 存在多个段落实际上处在同一个P元素中的情况
                    if (pList.size() > 1) {

                        docxCommonService.splitAndReplaceParagraph(
                                mainIterator,
                                pList,
                                ppr,
                                true
                        );

                        continue;
                    }

                    // 是否含有各类元素
                    boolean hasRun =
                            docxCommonService.hasRun(pContent);

                    boolean hasRunIns =
                            docxCommonService.hasRunIns(pContent);

                    boolean hasRunDel =
                            docxCommonService.hasRunDel(pContent);

                    boolean hasCommentStart =
                            docxCommonService.hasCommentStart(pContent);

                    boolean hasCommentEnd =
                            docxCommonService.hasCommentEnd(pContent);

                    // 获取当前段落文本内容
                    pgText =
                            docxCommonService
                                    .extractFullParagraphText(pContent)
                                    .trim();

                    // pgText为空也得处理，防止批注加在空处的特殊情况
                    pgTextBefore = pgText;

                    if (hasRunIns || hasRunDel) {

                        pgTextBefore =
                                docxCommonService
                                        .extractFullParagraphTextBeforeModify(pContent)
                                        .trim();
                    }

                    // 动态更新：当前模块ID + 标题层级前缀，
                    // 需要考虑全部删除的段落pgText为空的情况
                    textCurrentModuleId =
                            docxCommonService.getCurrentModule(
                                    StringUtils.isEmpty(pgText)
                                            ? pgTextBefore
                                            : pgText,
                                    textCurrentModuleId
                            );

                    docxCommonService.updateTextTitleLevelMap(
                            StringUtils.isEmpty(pgText)
                                    ? pgTextBefore
                                    : pgText,
                            textCurrentTitleMapNum,
                            textCurrentTitleMapText
                    );

                    // 有word自带序号前缀，替换为普通文本，并更新标题序号信息
                    if (ppr != null
                            && ppr.getNumPr() != null
                            && numbering != null) {

                        Map<String, String> titleModeMap =
                                docxCommonService.getTitleModeFromP(
                                        numbering,
                                        ppr
                                );

                        pgText =
                                docxCommonService.handleTitleMode(
                                        paragraph,
                                        ppr,
                                        rpr,
                                        pgText,
                                        titleModeMap,
                                        textCurrentTitleMapNum,
                                        false
                                );

                        if (hasRunIns || hasRunDel) {

                            pgTextBefore =
                                    docxCommonService.handleTitleMode(
                                            paragraph,
                                            ppr,
                                            rpr,
                                            pgTextBefore,
                                            titleModeMap,
                                            textCurrentTitleMapNum,
                                            false
                                    );
                        }

                        docxCommonService.updateTextTitleLevelMap(
                                pgText,
                                textCurrentTitleMapNum,
                                textCurrentTitleMapText
                        );
                    }

                    // 判断是否带标题前缀，以及获取前缀及前缀层级，
                    // 需要考虑全部删除的段落pgText为空的情况
                    int titleLevel =
                            docxCommonService.getCurrentValidTitleLevel(
                                    textCurrentTitleMapNum
                            );
                    // 获取当前最低级别的标题

                    String preNum =
                            docxCommonService.extractPrefixByLevel(
                                    StringUtils.isEmpty(pgText)
                                            ? pgTextBefore
                                            : pgText,
                                    titleLevel
                            );
                    // 按指定层级，从段落文本中直接正则匹配提取前缀

                    // 封装增强型段落对象
                    EnhanceObj enhanceObj = new EnhanceObj();

                    enhanceObj.setType(1);
                    enhanceObj.setP(paragraph);

                    enhanceObj.setPpr(
                            ppr == null
                                    ? factory.createPPr()
                                    : XmlUtils.deepCopy(ppr)
                    );

                    enhanceObj.setPText(pgText.trim());

                    enhanceObj.setPTextBefore(
                            pgTextBefore.trim()
                    );

                    enhanceObj.setTextCurrentModuleId(
                            textCurrentModuleId
                    );

                    enhanceObj.setTextCurrentTitleMapNum(
                            new HashMap<>(textCurrentTitleMapNum)
                    );

                    enhanceObj.setTextCurrentTitleMapText(
                            new HashMap<>(textCurrentTitleMapText)
                    );

                    enhanceObj.setHasRun(hasRun);
                    enhanceObj.setHasRunIns(hasRunIns);
                    enhanceObj.setHasRunDel(hasRunDel);

                    enhanceObj.setHasCommentStart(
                            hasCommentStart
                    );

                    enhanceObj.setHasCommentEnd(
                            hasCommentEnd
                    );

                    if (titleLevel > 0
                            && !StringUtils.isEmpty(preNum)) {

                        enhanceObj.setTitle(true);
                        enhanceObj.setTitleLevel(titleLevel);
                        enhanceObj.setTitleStr(preNum);

                    } else {

                        enhanceObj.setTitle(false);
                    }

                    // 存在批注开始元素
                    if (hasCommentStart) {

                        // 获取批注ID信息
                        List<String> commentIds =
                                docxCommonService.getCommentId(
                                        pContent,
                                        COMMENT_RANGE_START
                                );

                        // 在空批注ID集合中，有且仅有一个匹配上
                        List<String> matchList =
                                commentIds.stream()
                                        .filter(emptyCommentIds::contains)
                                        .collect(Collectors.toList());

                        isFalse(
                                matchList.size() > 1,
                                "修订记录中存在单段落多个空批注情况，请核对！"
                        );

                        if (matchList.size() == 1) {

                            String commentId =
                                    matchList.get(0);

                            enhanceObj.setCommentId(commentId);

                            // 创建批注范围
                            CommentRange commentRange =
                                    new CommentRange();

                            List<EnhanceObj> objInRange =
                                    new LinkedList<>();

                            objInRange.add(enhanceObj);

                            commentRange.setCommentId(commentId);
                            commentRange.setStartObj(enhanceObj);
                            commentRange.setObjInRange(objInRange);
                            commentRange.setAllDel(false);

                            if (enhanceObj.isHasRun()) {
                                commentRange.setHasRun(true);
                            }

                            if (enhanceObj.isHasRunIns()) {
                                commentRange.setHasRunIns(true);
                            }

                            if (enhanceObj.isHasRunDel()) {
                                commentRange.setHasRunDel(true);
                            }

                            Set<Integer> set =
                                    new HashSet<>();

                            commentRange.setPInRangeTitleLevel(
                                    new HashSet<>(set)
                            );

                            // 存在标题段落
                            if (enhanceObj.isTitle()) {

                                commentRange
                                        .getPInRangeTitleLevel()
                                        .add(titleLevel);
                            }

                            // 加入到批注范围结果集
                            commentRangeList.add(commentRange);

                            // 更新索引信息
                            commentRangeListIndex =
                                    commentRangeList.size() - 1;

                            // 更新空批注结尾寻找结果
                            findCommentOver = false;
                        }
                    }

                    // 存在批注结束元素
                    if (hasCommentEnd) {

                        // 获取批注ID信息
                        List<String> commentIds =
                                docxCommonService.getCommentId(
                                        pContent,
                                        COMMENT_RANGE_END
                                );

                        // 在空批注ID集合中，有且仅有一个匹配上
                        List<String> matchList =
                                commentIds.stream()
                                        .filter(emptyCommentIds::contains)
                                        .collect(Collectors.toList());

                        isFalse(
                                matchList.size() > 1,
                                "修订记录中存在单段落多个空批注情况，请核对！"
                        );

                        if (matchList.size() == 1) {

                            String commentId =
                                    matchList.get(0);

                            CommentRange commentRange =
                                    commentRangeList.get(
                                            commentRangeListIndex
                                    );

                            isFalse(
                                    !commentRange
                                            .getCommentId()
                                            .equals(commentId),
                                    "修订记录中存在空批注开始和结束不匹配情况，请联系技术人员处理。"
                            );

                            // 成功匹配上
                            commentRange.setEndObj(enhanceObj);

                            if (!enhanceObj.equals(
                                    commentRange.getStartObj())) {

                                commentRange
                                        .getObjInRange()
                                        .add(enhanceObj);

                                if (enhanceObj.isHasRun()) {
                                    commentRange.setHasRun(true);
                                }

                                if (enhanceObj.isHasRunIns()) {
                                    commentRange.setHasRunIns(true);
                                }

                                if (enhanceObj.isHasRunDel()) {
                                    commentRange.setHasRunDel(true);
                                }

                                // 存在标题段落
                                if (enhanceObj.isTitle()) {

                                    commentRange
                                            .getPInRangeTitleLevel()
                                            .add(titleLevel);
                                }
                            }

                            // 更新空批注结尾寻找结果
                            findCommentOver = true;
                        }
                    }

                    // 不存在批注开始和结束元素，但处在批注范围中间
                    if (!hasCommentStart
                            && !hasCommentEnd
                            && !findCommentOver) {

                        CommentRange commentRange =
                                commentRangeList.get(
                                        commentRangeListIndex
                                );

                        commentRange
                                .getObjInRange()
                                .add(enhanceObj);

                        if (enhanceObj.isHasRun()) {
                            commentRange.setHasRun(true);
                        }

                        if (enhanceObj.isHasRunIns()) {
                            commentRange.setHasRunIns(true);
                        }

                        if (enhanceObj.isHasRunDel()) {
                            commentRange.setHasRunDel(true);
                        }

                        // 存在标题段落
                        if (enhanceObj.isTitle()) {

                            commentRange
                                    .getPInRangeTitleLevel()
                                    .add(titleLevel);
                        }
                    }

                    // 不存在批注开始和结束元素，不在批注范围中间，
                    // 但是会是删除元素（考虑几个段落整体删除，没有批注信息的情况）
                    if (!hasCommentStart
                            && !hasCommentEnd
                            && findCommentOver
                            && hasRunDel
                            && !hasRunIns
                            && !hasRun) {

                        boolean allDel;

                        if (commentRangeList.size() == 0) {

                            allDel = false;

                        } else {

                            allDel =
                                    commentRangeList
                                            .get(commentRangeListIndex)
                                            .isAllDel();
                        }

                        String commentId = "";

                        docxCommonService.hasCommentReference(
                                pContent,
                                commentId
                        );

                        // 连续全删除段落中的第一个段落
                        if (!allDel) {

                            // 创建批注范围
                            CommentRange commentRange =
                                    new CommentRange();

                            List<EnhanceObj> objInRange =
                                    new LinkedList<>();

                            objInRange.add(enhanceObj);

                            commentRange.setStartObj(enhanceObj);
                            commentRange.setObjInRange(objInRange);
                            commentRange.setAllDel(true);

                            if (enhanceObj.isHasRunDel()) {
                                commentRange.setHasRunDel(true);
                            }

                            Set<Integer> set =
                                    new HashSet<>();

                            commentRange.setPInRangeTitleLevel(
                                    new HashSet<>(set)
                            );

                            // 存在标题段落
                            if (enhanceObj.isTitle()) {

                                commentRange
                                        .getPInRangeTitleLevel()
                                        .add(titleLevel);
                            }

                            // 加入到批注范围结果集
                            commentRangeList.add(commentRange);

                            // 更新索引信息
                            commentRangeListIndex =
                                    commentRangeList.size() - 1;

                        } else {

                            CommentRange commentRange =
                                    commentRangeList.get(
                                            commentRangeListIndex
                                    );

                            if (enhanceObj.isHasRunDel()) {
                                commentRange.setHasRunDel(true);
                            }

                            commentRange
                                    .getObjInRange()
                                    .add(enhanceObj);

                            // 存在标题段落
                            if (enhanceObj.isTitle()) {

                                commentRange
                                        .getPInRangeTitleLevel()
                                        .add(titleLevel);
                            }
                        }

                        // 找到结尾
                        if (!StringUtils.isEmpty(commentId)) {

                            CommentRange commentRange =
                                    commentRangeList.get(
                                            commentRangeListIndex
                                    );

                            List<EnhanceObj> objInRangeList =
                                    commentRange.getObjInRange();

                            commentRange.setCommentId(commentId);
                            commentRange.setEndObj(enhanceObj);

                            if (enhanceObj.isHasRunDel()) {
                                commentRange.setHasRunDel(true);
                            }

                            if (!enhanceObj.equals(
                                    objInRangeList.get(
                                            objInRangeList.size() - 1
                                    ))) {

                                commentRange
                                        .getObjInRange()
                                        .add(enhanceObj);
                            }

                            // 存在标题段落
                            if (enhanceObj.isTitle()) {

                                commentRange
                                        .getPInRangeTitleLevel()
                                        .add(titleLevel);
                            }
                        }
                    }

                    /*
                     * 20260717业务要求暂时回退该逻辑，保持空批注方案
                     * 不存在批注开始和结束元素，不在批注范围中间，
                     * 但是含修订痕迹的段落，单独段落处理
                     *
                    if (!hasCommentStart
                            && !hasCommentEnd
                            && findCommentOver
                            && (hasRunDel || hasRunIns)) {

                        // 创建批注范围
                        CommentRange commentRange =
                                new CommentRange();

                        List<EnhanceP> pInRange =
                                new LinkedList<>();

                        pInRange.add(enhanceP);

                        commentRange.setStartP(enhanceP);
                        commentRange.setEndP(enhanceP);
                        commentRange.setPInRange(pInRange);

                        if (enhanceP.isHasRun()) {
                            commentRange.setHasRun(true);
                        }

                        if (enhanceP.isHasRunIns()) {
                            commentRange.setHasRunIns(true);
                        }

                        if (enhanceP.isHasRunDel()) {
                            commentRange.setHasRunDel(true);
                        }

                        // 存在标题段落
                        Set<Integer> set =
                                new HashSet<>();

                        commentRange.setPInRangeTitleLevel(
                                new HashSet<>(set)
                        );

                        if (enhanceP.isTitle()) {

                            commentRange
                                    .getPInRangeTitleLevel()
                                    .add(titleLevel);
                        }

                        // 加入到批注范围结果集
                        commentRangeList.add(commentRange);

                        // 更新索引信息
                        commentRangeListIndex =
                                commentRangeList.size() - 1;
                    }
                    */

                } else {

                    // 处理表格，初始化表格列表，收集当前节点中的所有Tbl
                    List<Tbl> tblList = new ArrayList<>();

                    docxCommonService.findAllTblInNode(
                            obj,
                            tblList
                    );

                    if (!tblList.isEmpty()) {

                        for (Tbl table : tblList) {

                            List<Object> tableContent =
                                    table.getContent();

                            boolean haveInsOrDelInPara =
                                    haveInsOrDelInPara(table);

                            boolean hasRun =
                                    docxCommonService.hasRun(
                                            tableContent
                                    );

                            boolean hasRunIns =
                                    docxCommonService.hasRunIns(
                                            tableContent
                                    );

                            boolean hasRunDel =
                                    docxCommonService.hasRunDel(
                                            tableContent
                                    );

                            boolean hasCommentStart =
                                    docxCommonService.hasCommentStart(
                                            tableContent
                                    );

                            boolean hasCommentEnd =
                                    docxCommonService.hasCommentEnd(
                                            tableContent
                                    );

                            EnhanceObj enhanceObj =
                                    new EnhanceObj();

                            enhanceObj.setType(2);

                            enhanceObj.setTextCurrentModuleId(
                                    textCurrentModuleId
                            );

                            enhanceObj.setTextCurrentTitleMapNum(
                                    new HashMap<>(
                                            textCurrentTitleMapNum
                                    )
                            );

                            enhanceObj.setTextCurrentTitleMapText(
                                    new HashMap<>(
                                            textCurrentTitleMapText
                                    )
                            );

                            enhanceObj.setHasRun(hasRun);
                            enhanceObj.setHasRunIns(hasRunIns);
                            enhanceObj.setHasRunDel(hasRunDel);

                            enhanceObj.setHasCommentStart(
                                    hasCommentStart
                            );

                            enhanceObj.setHasCommentEnd(
                                    hasCommentEnd
                            );

                            enhanceObj.setTitle(false);

                            Tbl tblOld =
                                    XmlUtils.deepCopy(table);

                            Tbl tblNew =
                                    XmlUtils.deepCopy(table);

                            getParaContent(tblOld, false);
                            getParaContent(tblNew, true);

                            enhanceObj.setTableOld(tblOld);
                            enhanceObj.setTableNew(tblNew);

                            // 存在批注开始元素
                            if (hasCommentStart) {

                                // 获取批注ID信息
                                List<String> commentIds =
                                        docxCommonService.getCommentId(
                                                tableContent,
                                                COMMENT_RANGE_START
                                        );

                                // 在空批注ID集合中，有且仅有一个匹配上
                                List<String> matchList =
                                        commentIds.stream()
                                                .filter(emptyCommentIds::contains)
                                                .collect(Collectors.toList());

                                isFalse(
                                        matchList.size() > 1,
                                        "修订记录中存在单段落多个空批注情况，请核对！"
                                );

                                if (matchList.size() == 1) {

                                    String commentId =
                                            matchList.get(0);

                                    enhanceObj.setCommentId(commentId);

                                    // 创建批注范围
                                    CommentRange commentRange =
                                            new CommentRange();

                                    List<EnhanceObj> objInRange =
                                            new LinkedList<>();

                                    objInRange.add(enhanceObj);

                                    commentRange.setCommentId(commentId);
                                    commentRange.setStartObj(enhanceObj);
                                    commentRange.setObjInRange(objInRange);
                                    commentRange.setHasTable(true);
                                    commentRange.setAllDel(false);

                                    if (enhanceObj.isHasRun()) {
                                        commentRange.setHasRun(true);
                                    }

                                    if (enhanceObj.isHasRunIns()) {
                                        commentRange.setHasRunIns(true);
                                    }

                                    if (enhanceObj.isHasRunDel()) {
                                        commentRange.setHasRunDel(true);
                                    }

                                    // 空的标题级别集合
                                    Set<Integer> set =
                                            new HashSet<>();

                                    commentRange.setPInRangeTitleLevel(
                                            new HashSet<>(set)
                                    );

                                    // 加入到批注范围结果集
                                    commentRangeList.add(commentRange);

                                    // 更新索引信息
                                    commentRangeListIndex =
                                            commentRangeList.size() - 1;

                                    // 更新空批注结尾寻找结果
                                    findCommentOver = false;
                                }
                            }

                            // 存在批注结束元素
                            if (hasCommentEnd) {

                                // 获取批注ID信息
                                List<String> commentIds =
                                        docxCommonService.getCommentId(
                                                tableContent,
                                                COMMENT_RANGE_END
                                        );

                                // 在空批注ID集合中，有且仅有一个匹配上
                                List<String> matchList =
                                        commentIds.stream()
                                                .filter(emptyCommentIds::contains)
                                                .collect(Collectors.toList());

                                isFalse(
                                        matchList.size() > 1,
                                        "修订记录中存在单段落多个空批注情况，请核对！"
                                );

                                if (matchList.size() == 1) {

                                    String commentId =
                                            matchList.get(0);

                                    CommentRange commentRange =
                                            commentRangeList.get(
                                                    commentRangeListIndex
                                            );

                                    isFalse(
                                            !commentRange
                                                    .getCommentId()
                                                    .equals(commentId),
                                            "修订记录中存在空批注开始和结束不匹配情况，请联系技术人员处理。"
                                    );

                                    // 成功匹配上
                                    commentRange.setEndObj(enhanceObj);
                                    commentRange.setHasTable(true);

                                    if (!enhanceObj.equals(
                                            commentRange.getStartObj())) {

                                        commentRange
                                                .getObjInRange()
                                                .add(enhanceObj);

                                        if (enhanceObj.isHasRun()) {
                                            commentRange.setHasRun(true);
                                        }

                                        if (enhanceObj.isHasRunIns()) {
                                            commentRange.setHasRunIns(true);
                                        }

                                        if (enhanceObj.isHasRunDel()) {
                                            commentRange.setHasRunDel(true);
                                        }
                                    }

                                    // 更新空批注结尾寻找结果
                                    findCommentOver = true;
                                }
                            }

                            // 不存在批注开始和结束元素，但处在批注范围中间
                            if (!hasCommentStart
                                    && !hasCommentEnd
                                    && !findCommentOver) {

                                CommentRange commentRange =
                                        commentRangeList.get(
                                                commentRangeListIndex
                                        );

                                commentRange
                                        .getObjInRange()
                                        .add(enhanceObj);

                                commentRange.setHasTable(true);

                                if (enhanceObj.isHasRun()) {
                                    commentRange.setHasRun(true);
                                }

                                if (enhanceObj.isHasRunIns()) {
                                    commentRange.setHasRunIns(true);
                                }

                                if (enhanceObj.isHasRunDel()) {
                                    commentRange.setHasRunDel(true);
                                }
                            }

                            // 没有在批注范围中，但是有修订痕迹的情况
                            if (!hasCommentStart
                                    && !hasCommentEnd
                                    && findCommentOver
                                    && haveInsOrDelInPara) {

                                // 创建批注范围
                                CommentRange commentRange =
                                        new CommentRange();

                                List<EnhanceObj> objInRange =
                                        new LinkedList<>();

                                objInRange.add(enhanceObj);

                                commentRange.setTableModifyNoComment(true);
                                commentRange.setStartObj(enhanceObj);
                                commentRange.setEndObj(enhanceObj);
                                commentRange.setObjInRange(objInRange);
                                commentRange.setHasTable(true);
                                commentRange.setAllDel(false);

                                if (enhanceObj.isHasRun()) {
                                    commentRange.setHasRun(true);
                                }

                                if (enhanceObj.isHasRunIns()) {
                                    commentRange.setHasRunIns(true);
                                }

                                if (enhanceObj.isHasRunDel()) {
                                    commentRange.setHasRunDel(true);
                                }

                                // 空的标题级别集合
                                Set<Integer> set =
                                        new HashSet<>();

                                commentRange.setPInRangeTitleLevel(
                                        new HashSet<>(set)
                                );

                                // 加入到批注范围结果集
                                commentRangeList.add(commentRange);

                                // 更新索引信息
                                commentRangeListIndex =
                                        commentRangeList.size() - 1;
                            }
                        }
                    }
                }
            }

            // 6.5 删除部分情况，使用迭代器自身remove，安全
            Iterator<CommentRange> commentRangeListIterator =
                    commentRangeList.iterator();

            while (commentRangeListIterator.hasNext()) {

                CommentRange commentRange =
                        commentRangeListIterator.next();

                String commentId =
                        commentRange.getCommentId();

                EnhanceObj endObj =
                        commentRange.getEndObj();

                boolean allDel =
                        commentRange.isAllDel();

                boolean hasRunIns =
                        commentRange.isHasRunIns();

                boolean hasRunDel =
                        commentRange.isHasRunDel();

                // 删除：不存在批注开始和结束元素，不在批注范围中间，
                // 但是全是删除元素，但是却属于纯修订，没有加批注后删除的情况
                if (allDel
                        && (StringUtils.isEmpty(commentId)
                        || endObj == null)) {

                    commentRangeListIterator.remove();
                }

                // 删除：虽被空批注包含，但是其中没有任何修订痕迹
                if (!hasRunIns && !hasRunDel) {

                    commentRangeListIterator.remove();
                }
            }

            // 遍历空批注范围结果集
            for (int i = 0;
                 i < commentRangeList.size();
                 i++) {

                CommentRange commentRange =
                        commentRangeList.get(i);

                List<EnhanceObj> objInRangeList =
                        commentRange.getObjInRange();

                String commentId =
                        commentRange.getCommentId();

                // 同一个批注范围中的模块、一级标题需相同；
                // 二三四级标题可以不同，若几个段落的二三四级相同保留，若不相同则置为""
                long preTextModuleId =
                        CONTRACT_ANALYSIS_MODULE_INIT;

                String preTextTitleStrFirst = "";
                String preTextTitleStrSecond = "";
                String preTextTitleStrThird = "";
                String preTextTitleStrFourth = "";

                String preTextTitleStrContentFirst = "";
                String preTextTitleStrContentSecond = "";
                String preTextTitleStrContentThird = "";
                String preTextTitleStrContentFourth = "";

                for (int j = 0;
                     j < objInRangeList.size();
                     j++) {

                    EnhanceObj enhanceObj =
                            objInRangeList.get(j);

                    long textModuleId =
                            enhanceObj.getTextCurrentModuleId();

                    String textTitleStrFirst =
                            enhanceObj
                                    .getTextCurrentTitleMapNum()
                                    .get(1);

                    String textTitleStrContentFirst =
                            enhanceObj
                                    .getTextCurrentTitleMapText()
                                    .get(1);

                    String textTitleStrSecond =
                            enhanceObj
                                    .getTextCurrentTitleMapNum()
                                    .get(2);

                    String textTitleStrContentSecond =
                            enhanceObj
                                    .getTextCurrentTitleMapText()
                                    .get(2);

                    String textTitleStrThird =
                            enhanceObj
                                    .getTextCurrentTitleMapNum()
                                    .get(3);

                    String textTitleStrContentThird =
                            enhanceObj
                                    .getTextCurrentTitleMapText()
                                    .get(3);

                    String textTitleStrFourth =
                            enhanceObj
                                    .getTextCurrentTitleMapNum()
                                    .get(4);

                    String textTitleStrContentFourth =
                            enhanceObj
                                    .getTextCurrentTitleMapText()
                                    .get(4);

                    if (j == 0) {

                        preTextModuleId =
                                textModuleId;

                        preTextTitleStrFirst =
                                textTitleStrFirst;

                        preTextTitleStrContentFirst =
                                textTitleStrContentFirst;

                        preTextTitleStrSecond =
                                textTitleStrSecond;

                        preTextTitleStrContentSecond =
                                textTitleStrContentSecond;

                        preTextTitleStrThird =
                                textTitleStrThird;

                        preTextTitleStrContentThird =
                                textTitleStrContentThird;

                        preTextTitleStrFourth =
                                textTitleStrFourth;

                        preTextTitleStrContentFourth =
                                textTitleStrContentFourth;

                    } else {

                        isFalse(
                                textModuleId != preTextModuleId,
                                "存在同个空批注跨多个模块的情况（如不可批注内的一部分内容在目录、一部分内容在正文），请核对！"
                        );

                        isFalse(
                                !textTitleStrFirst.equals(
                                        preTextTitleStrFirst
                                )
                                        || !textTitleStrContentFirst.equals(
                                        preTextTitleStrContentFirst
                                ),
                                "存在同个空批注跨多个一级标题的情况，请核对！"
                        );

                        // 如果二至四级标题不一致则置空
                        if (!textTitleStrSecond.equals(
                                preTextTitleStrSecond
                        )
                                || !textTitleStrContentSecond.equals(
                                preTextTitleStrContentSecond
                        )) {

                            preTextTitleStrSecond = "";
                            preTextTitleStrContentSecond = "";
                        }

                        if (!textTitleStrThird.equals(
                                preTextTitleStrThird
                        )
                                || !textTitleStrContentThird.equals(
                                preTextTitleStrContentThird
                        )) {

                            preTextTitleStrThird = "";
                            preTextTitleStrContentThird = "";
                        }

                        if (!textTitleStrFourth.equals(
                                preTextTitleStrFourth
                        )
                                || !textTitleStrContentFourth.equals(
                                preTextTitleStrContentFourth
                        )) {

                            preTextTitleStrFourth = "";
                            preTextTitleStrContentFourth = "";
                        }
                    }
                }

                commentRange.setTextCurrentModuleId(
                        preTextModuleId
                );

                commentRange.setTextTitleStrFirst(
                        preTextTitleStrFirst
                );

                commentRange.setTextTitleStrSecond(
                        preTextTitleStrSecond
                );

                commentRange.setTextTitleStrThird(
                        preTextTitleStrThird
                );

                commentRange.setTextTitleStrFourth(
                        preTextTitleStrFourth
                );

                commentRange.setTextTitleStrContentFirst(
                        preTextTitleStrContentFirst
                );

                commentRange.setTextTitleStrContentSecond(
                        preTextTitleStrContentSecond
                );

                commentRange.setTextTitleStrContentThird(
                        preTextTitleStrContentThird
                );

                commentRange.setTextTitleStrContentFourth(
                        preTextTitleStrContentFourth
                );
            }

            // 提取修订信息，替换补充协议模板
            List<Object> changeContentList =
                    getChangeContent(
                            commentRangeList,
                            generateByRevisionType
                    );

            Map<String, Object> changeContentMap =
                    new HashMap<>();

            if (configList.size() > 0) {

                for (ReplaceContentConfiguration m : configList) {

                    changeContentMap.put(
                            m.getOldContent(),
                            m.getNewContent()
                    );
                }
            }

            changeContentMap.put(
                    AGREEMENT_PLACEHOLDERS,
                    changeContentList
            );

            res = docxReplaceContent.replaceContent(
                    "",
                    supplementFileSavePath,
                    xmlLoadResSupple,
                    changeContentMap,
                    null,
                    isAllText,
                    isModify
            );

        } catch (Exception e) {

            log.error(
                    "文档解析生成失败！修订版合同获取地址：{}，补充协议文件获取地址：{}",
                    revisionFileGetPath,
                    supplementFileGetPath,
                    e
            );

            throw new RuntimeException(
                    e.getMessage(),
                    e
            );
        }

        // 返回文本提示
        return res;
    }

    /**
     * 提取变更内容
     */
    public List<Object> getChangeContent(List<CommentRange> commentRangeList,
                                         Integer generateByRevisionType) {
        List<Object> res = new LinkedList<>();

        if (commentRangeList != null && commentRangeList.size() > 0) {

            // 创建一个仿宋、小四、两端对齐，固定行距（22磅）、首行缩进2字符的段落P
            P staticP = createStaticPara();

            for (int i = 0; i < commentRangeList.size(); i++) {

                CommentRange commentRange = commentRangeList.get(i);

                if (commentRange == null) {
                    continue;
                }

                StringBuilder sb = new StringBuilder();

                boolean allDel = commentRange.isAllDel();
                boolean hasRun = commentRange.isHasRun();
                boolean hasRunIns = commentRange.isHasRunIns();
                boolean hasRunDel = commentRange.isHasRunDel();

                List<EnhanceObj> objInRange =
                        commentRange.getObjInRange();

                Set<Integer> pInRangeTitleLevel =
                        commentRange.getPInRangeTitleLevel();

                long textCurrentModuleId =
                        commentRange.getTextCurrentModuleId();

                String textCurrentModuleStr =
                        MODULE_NAME_MAP.get(textCurrentModuleId);

                String textTitleStrFirst =
                        commentRange.getTextTitleStrFirst();

                String textTitleStrSecond =
                        commentRange.getTextTitleStrSecond();

                String textTitleStrThird =
                        commentRange.getTextTitleStrThird();

                String textTitleStrFourth =
                        commentRange.getTextTitleStrFourth();

                String textTitleStrContentFirst =
                        commentRange.getTextTitleStrContentFirst();

                String textTitleStrContentSecond =
                        commentRange.getTextTitleStrContentSecond();

                String textTitleStrContentThird =
                        commentRange.getTextTitleStrContentThird();

                String textTitleStrContentFourth =
                        commentRange.getTextTitleStrContentFourth();

                // 拼接变更表述

                // 仅有删除
                if (allDel || (hasRunDel && !hasRunIns && !hasRun)) {

                    // 除了第一段，其余的都加上一个空行，显示上更加友好
                    if (i > 0) {
                        P preP = docxCommonService.createParagraphWithSameStyle(
                                staticP,
                                "    ",
                                false
                        );
                        res.add(preP);
                    }

                    sb.append("<bold>")
                            .append(String.valueOf(i + 1))
                            .append("、《基金合同》")
                            .append(textCurrentModuleStr);

                    // 如果有1级标题且不为空，且长度小于50
                    if (!StringUtils.isEmpty(textTitleStrContentFirst)
                            && textTitleStrContentFirst.length() <= 50) {

                        sb.append("第")
                                .append(textTitleStrFirst.replace("、", ""))
                                .append("节“")
                                .append(textTitleStrContentFirst)
                                .append("”");
                    }

                    // 如果有2级标题且不为空，且长度小于50，
                    // 且已有1级标题，且范围内所有段落不存在2级标题
                    if (!StringUtils.isEmpty(textTitleStrContentSecond)
                            && textTitleStrContentSecond.length() <= 50
                            && !pInRangeTitleLevel.contains(2)) {

                        sb.append("第")
                                .append(textTitleStrSecond)
                                .append("条“")
                                .append(textTitleStrContentSecond)
                                .append("”");
                    }

                    // 如果有3级标题且不为空，且长度小于50，
                    // 且已有2级标题，且范围内所有段落不存在3级标题
                    if (!StringUtils.isEmpty(textTitleStrContentThird)
                            && textTitleStrContentThird.length() <= 50
                            && !pInRangeTitleLevel.contains(3)) {

                        sb.append("第")
                                .append(textTitleStrThird.replace("、", ""))
                                .append("款“")
                                .append(textTitleStrContentThird)
                                .append("”");
                    }

                    // 如果有4级标题且不为空，且长度小于50，
                    // 且已有3级标题，且范围内所有段落不存在4级标题
                    if (!StringUtils.isEmpty(textTitleStrContentFourth)
                            && textTitleStrContentFourth.length() <= 50
                            && !pInRangeTitleLevel.contains(4)) {

                        sb.append("第")
                                .append(textTitleStrFourth)
                                .append("项“")
                                .append(textTitleStrContentFourth)
                                .append("”");
                    }

                    sb.append("删除如下约定：</bold>");

                    String title = sb.toString().trim();

                    P titleP =
                            docxCommonService.createParagraphWithSameStyle(
                                    staticP,
                                    title,
                                    false
                            );

                    res.add(titleP);

                    // 遍历空批注范围内的段对象列表
                    for (int j = 0; j < objInRange.size(); j++) {

                        EnhanceObj enhanceObj =
                                objInRange.get(j);

                        int type = enhanceObj.getType();

                        sb.setLength(0);

                        // 如果是段落
                        if (type == 1) {

                            if (j == 0) {
                                sb.append("“");
                            }

                            sb.append(
                                    enhanceObj.getPTextBefore()
                            );

                            if (j == objInRange.size() - 1) {
                                sb.append("”");
                            }

                            P contentPreP =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    staticP,
                                                    sb.toString().trim(),
                                                    false
                                            );

                            res.add(contentPreP);
                        }

                        // 如果是表格
                        if (type == 2) {

                            if (j == 0) {

                                P p1 =
                                        docxCommonService
                                                .createParagraphWithSameStyle(
                                                        staticP,
                                                        "“",
                                                        false
                                                );

                                res.add(p1);
                            }

                            res.add(
                                    enhanceObj.getTableOld()
                            );

                            if (j == objInRange.size() - 1) {

                                P p2 =
                                        docxCommonService
                                                .createParagraphWithSameStyle(
                                                        staticP,
                                                        "”",
                                                        false
                                                );

                                res.add(p2);
                            }
                        }
                    }

                    // 最后一段的后面加上一个空行，显示上更加友好
                    if (i == commentRangeList.size() - 1) {

                        P lastP =
                                docxCommonService
                                        .createParagraphWithSameStyle(
                                                staticP,
                                                "    ",
                                                false
                                        );

                        res.add(lastP);
                    }

                } else {

                    // 仅有新增
                    if (hasRunIns && !hasRunDel && !hasRun) {

                        // 除了第一段，其余的都加上一个空行，显示上更加友好
                        if (i > 0) {

                            P preP =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    staticP,
                                                    "    ",
                                                    false
                                            );

                            res.add(preP);
                        }

                        sb.append("<bold>")
                                .append(String.valueOf(i + 1))
                                .append("、《基金合同》")
                                .append(textCurrentModuleStr);

                        // 如果有1级标题且不为空，且长度小于50
                        if (!StringUtils.isEmpty(textTitleStrContentFirst)
                                && textTitleStrContentFirst.length() <= 50) {

                            sb.append("第")
                                    .append(textTitleStrFirst.replace("、", ""))
                                    .append("节“")
                                    .append(textTitleStrContentFirst)
                                    .append("”");
                        }

                        // 如果有2级标题且不为空，且长度小于50，
                        // 且已有1级标题，且范围内所有段落不存在2级标题
                        if (!StringUtils.isEmpty(textTitleStrContentSecond)
                                && textTitleStrContentSecond.length() <= 50
                                && !pInRangeTitleLevel.contains(2)) {

                            sb.append("第")
                                    .append(textTitleStrSecond)
                                    .append("条“")
                                    .append(textTitleStrContentSecond)
                                    .append("”");
                        }

                        // 如果有3级标题且不为空，且长度小于50，
                        // 且已有2级标题，且范围内所有段落不存在3级标题
                        if (!StringUtils.isEmpty(textTitleStrContentThird)
                                && textTitleStrContentThird.length() <= 50
                                && !pInRangeTitleLevel.contains(3)) {

                            sb.append("第")
                                    .append(textTitleStrThird.replace("、", ""))
                                    .append("款“")
                                    .append(textTitleStrContentThird)
                                    .append("”");
                        }

                        // 如果有4级标题且不为空，且长度小于50，
                        // 且已有3级标题，且范围内所有段落不存在4级标题
                        if (!StringUtils.isEmpty(textTitleStrContentFourth)
                                && textTitleStrContentFourth.length() <= 50
                                && !pInRangeTitleLevel.contains(4)) {

                            sb.append("第")
                                    .append(textTitleStrFourth)
                                    .append("项“")
                                    .append(textTitleStrContentFourth)
                                    .append("”");
                        }

                        sb.append("新增如下约定：</bold>");

                        String title =
                                sb.toString().trim();

                        P titleP =
                                docxCommonService
                                        .createParagraphWithSameStyle(
                                                staticP,
                                                title,
                                                false
                                        );

                        res.add(titleP);

                        // 遍历空批注范围内的段对象列表
                        for (int j = 0; j < objInRange.size(); j++) {

                            EnhanceObj enhanceObj =
                                    objInRange.get(j);

                            int type =
                                    enhanceObj.getType();

                            sb.setLength(0);

                            // 如果是段落
                            if (type == 1) {

                                if (j == 0) {
                                    sb.append("“");
                                }

                                sb.append(
                                        enhanceObj.getPText()
                                );

                                if (j == objInRange.size() - 1) {
                                    sb.append("”");
                                }

                                P contentNewP =
                                        docxCommonService
                                                .createParagraphWithSameStyle(
                                                        staticP,
                                                        sb.toString().trim(),
                                                        false
                                                );

                                res.add(contentNewP);
                            }

                            // 如果是表格
                            if (type == 2) {

                                if (j == 0) {

                                    P p1 =
                                            docxCommonService
                                                    .createParagraphWithSameStyle(
                                                            staticP,
                                                            "“",
                                                            false
                                                    );

                                    res.add(p1);
                                }

                                res.add(
                                        enhanceObj.getTableNew()
                                );

                                if (j == objInRange.size() - 1) {

                                    P p2 =
                                            docxCommonService
                                                    .createParagraphWithSameStyle(
                                                            staticP,
                                                            "”",
                                                            false
                                                    );

                                    res.add(p2);
                                }
                            }
                        }

                        // 最后一段的后面加上一个空行，显示上更加友好
                        if (i == commentRangeList.size() - 1) {

                            P lastP =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    staticP,
                                                    "    ",
                                                    false
                                            );

                            res.add(lastP);
                        }

                    } else {

                        // 其余情况归为变更

                        // 除了第一段，其余的都加上一个空行，显示上更加友好
                        if (i > 0) {

                            P preP =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    staticP,
                                                    "    ",
                                                    false
                                            );

                            res.add(preP);
                        }

                        sb.append("<bold>")
                                .append(String.valueOf(i + 1))
                                .append("、《基金合同》")
                                .append(textCurrentModuleStr);

                        // 如果有1级标题且不为空，且长度小于50
                        if (!StringUtils.isEmpty(textTitleStrContentFirst)
                                && textTitleStrContentFirst.length() <= 50) {

                            sb.append("第")
                                    .append(textTitleStrFirst.replace("、", ""))
                                    .append("节“")
                                    .append(textTitleStrContentFirst)
                                    .append("”");
                        }

                        // 如果有2级标题且不为空，且长度小于50，
                        // 且已有1级标题，且范围内所有段落不存在2级标题
                        if (!StringUtils.isEmpty(textTitleStrContentSecond)
                                && textTitleStrContentSecond.length() <= 50
                                && !pInRangeTitleLevel.contains(2)) {

                            sb.append("第")
                                    .append(textTitleStrSecond)
                                    .append("条“")
                                    .append(textTitleStrContentSecond)
                                    .append("”");
                        }

                        // 如果有3级标题且不为空，且长度小于50，
                        // 且已有2级标题，且范围内所有段落不存在3级标题
                        if (!StringUtils.isEmpty(textTitleStrContentThird)
                                && textTitleStrContentThird.length() <= 50
                                && !pInRangeTitleLevel.contains(3)) {

                            sb.append("第")
                                    .append(textTitleStrThird.replace("、", ""))
                                    .append("款“")
                                    .append(textTitleStrContentThird)
                                    .append("”");
                        }

                        // 如果有4级标题且不为空，且长度小于50，
                        // 且已有3级标题，且范围内所有段落不存在4级标题
                        if (!StringUtils.isEmpty(textTitleStrContentFourth)
                                && textTitleStrContentFourth.length() <= 50
                                && !pInRangeTitleLevel.contains(4)) {

                            sb.append("第")
                                    .append(textTitleStrFourth)
                                    .append("项“")
                                    .append(textTitleStrContentFourth)
                                    .append("”");
                        }

                        sb.append("如下约定：</bold>");

                        String title =
                                sb.toString().trim();

                        P titleP =
                                docxCommonService
                                        .createParagraphWithSameStyle(
                                                staticP,
                                                title,
                                                false
                                        );

                        res.add(titleP);

                        // 遍历空批注范围内的对象列表
                        for (int j = 0; j < objInRange.size(); j++) {

                            EnhanceObj enhanceObj =
                                    objInRange.get(j);

                            int type =
                                    enhanceObj.getType();

                            sb.setLength(0);

                            // 如果是段落
                            if (type == 1) {

                                String pTextBefore =
                                        enhanceObj.getPTextBefore();

                                if (StringUtils.isEmpty(pTextBefore)) {
                                    continue;
                                }

                                if (j == 0) {
                                    sb.append("“");
                                }

                                sb.append(pTextBefore);

                                if (j == objInRange.size() - 1) {
                                    sb.append("”");
                                }

                                P contentPreP =
                                        docxCommonService
                                                .createParagraphWithSameStyle(
                                                        staticP,
                                                        sb.toString().trim(),
                                                        false
                                                );

                                res.add(contentPreP);
                            }

                            // 如果是表格
                            if (type == 2) {

                                Tbl tableOld =
                                        enhanceObj.getTableOld();

                                if (tableIsEmpty(tableOld)) {
                                    continue;
                                }

                                if (j == 0) {

                                    P p1 =
                                            docxCommonService
                                                    .createParagraphWithSameStyle(
                                                            staticP,
                                                            "“",
                                                            false
                                                    );

                                    res.add(p1);
                                }

                                res.add(tableOld);

                                if (j == objInRange.size() - 1) {

                                    P p2 =
                                            docxCommonService
                                                    .createParagraphWithSameStyle(
                                                            staticP,
                                                            "”",
                                                            false
                                                    );

                                    res.add(p2);
                                }
                            }
                        }

                        // 加入中间表述
                        String content =
                                "<bold>自本协议的变更执行日起，上述内容变更如下：</bold>";

                        // 补充协议
                        if (generateByRevisionType == 1) {

                            content =
                                    "<bold>自本协议的变更执行日起，上述内容变更如下：</bold>";
                        }

                        // 征询函
                        if (generateByRevisionType == 2) {

                            content =
                                    "<bold>上述内容变更如下：</bold>";
                        }

                        P middleP =
                                docxCommonService
                                        .createParagraphWithSameStyle(
                                                staticP,
                                                content,
                                                false
                                        );

                        res.add(middleP);

                        // 再次遍历空批注范围内的对象列表
                        for (int j = 0; j < objInRange.size(); j++) {

                            EnhanceObj enhanceObj =
                                    objInRange.get(j);

                            int type =
                                    enhanceObj.getType();

                            sb.setLength(0);

                            // 如果是段落
                            if (type == 1) {

                                String pText =
                                        enhanceObj.getPText();

                                if (StringUtils.isEmpty(pText)) {
                                    continue;
                                }

                                if (j == 0) {
                                    sb.append("“");
                                }

                                sb.append(pText);

                                if (j == objInRange.size() - 1) {
                                    sb.append("”");
                                }

                                P contentNewP =
                                        docxCommonService
                                                .createParagraphWithSameStyle(
                                                        staticP,
                                                        sb.toString().trim(),
                                                        false
                                                );

                                res.add(contentNewP);
                            }

                            // 如果是表格
                            if (type == 2) {

                                Tbl tableNew =
                                        enhanceObj.getTableNew();

                                if (tableIsEmpty(tableNew)) {
                                    continue;
                                }

                                if (j == 0) {

                                    P p1 =
                                            docxCommonService
                                                    .createParagraphWithSameStyle(
                                                            staticP,
                                                            "“",
                                                            false
                                                    );

                                    res.add(p1);
                                }

                                res.add(tableNew);

                                if (j == objInRange.size() - 1) {

                                    P p2 =
                                            docxCommonService
                                                    .createParagraphWithSameStyle(
                                                            staticP,
                                                            "”",
                                                            false
                                                    );

                                    res.add(p2);
                                }
                            }
                        }

                        // 最后一段的后面加上一个空行，显示上更加友好
                        if (i == commentRangeList.size() - 1) {

                            P lastP =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    staticP,
                                                    "    ",
                                                    false
                                            );

                            res.add(lastP);
                        }
                    }
                }
            }
        }

        return res;
    }

    /**
     * 获取所有空批注
     */
    public List<String> getAllEmptyComments(WordprocessingMLPackage xmlLoadResRevision) {
        Comments comments = null;
        List<String> emptyCommentIds = new ArrayList<>(); // 存储空批注ID

        if (xmlLoadResRevision.getMainDocumentPart().getCommentsPart() != null) {
            comments = xmlLoadResRevision.getMainDocumentPart().getCommentsPart().getJaxbElement();
        }

        if (comments != null) {
            for (Comments.Comment comment : comments.getComment()) {
                String commentId = comment.getId().toString();

                // 是否是空批注
                if (docxCommonService.isEmptyComment(comment)) {
                    emptyCommentIds.add(commentId);
                }
            }
        }

        return emptyCommentIds;
    }

    /**
     * 判断表格元素中的段落中是否有修订痕迹
     */
    public boolean haveInsOrDelInPara(Tbl table) {
        boolean res = false;
        List<Object> tableRows = table.getContent();

        if (!CollectionUtils.isEmpty(tableRows)) {
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

                        List<Object> pContent = para.getContent();

                        // 是否含有修订元素
                        boolean hasRunIns = docxCommonService.hasRunIns(pContent);
                        boolean hasRunDel = docxCommonService.hasRunDel(pContent);

                        if (hasRunIns || hasRunDel) {
                            return true;
                        }
                    }
                }
            }
        }

        return res;
    }

    /**
     * 判断表格元素中的段落中是否有批注开始或结束
     * <p>
     * type：1|开始；2|结束
     */
    public boolean haveCommentStartOrEndInPara(Tbl table, int type) {
        boolean res = false;
        List<Object> tableRows = table.getContent();

        if (!CollectionUtils.isEmpty(tableRows)) {
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

                        List<Object> pContent = para.getContent();

                        // 是否含有批注相关元素
                        boolean hasCommentStart = docxCommonService.hasCommentStart(pContent);
                        boolean hasCommentEnd = docxCommonService.hasCommentEnd(pContent);

                        if (type == 1 && hasCommentStart) {
                            return true;
                        }

                        if (type == 2 && hasCommentEnd) {
                            return true;
                        }
                    }
                }
            }
        }

        return res;
    }

    /**
     * 将表格元素中的段落中的修订痕迹消除，获取原本文字内容
     * <p>
     * isNew true获取最新文本内容；false获取原本文本内容
     */
    public void getParaContent(Tbl table, boolean isNew) {
        List<Object> tableRows = table.getContent();

        if (!CollectionUtils.isEmpty(tableRows)) {

            // 存放表格中需要删除的空行，先收集，最后统一删除，避免并发修改异常
            List<Tr> toRemoveTrList = new ArrayList<>();

            for (Object tmp : tableRows) {
                Tr tr = tmp instanceof JAXBElement
                        ? (Tr) (((JAXBElement<?>) tmp).getValue())
                        : (Tr) tmp;

                boolean allCellEmpty = true;
                // 标记：本行的单元格是否全部为空
                // （即全部为新增的内容：新增的一行，则不应该出现在旧表格中）

                for (Object cellObj : tr.getContent()) {
                    Tc tc = cellObj instanceof JAXBElement
                            ? (Tc) (((JAXBElement<?>) cellObj).getValue())
                            : (Tc) cellObj;

                    for (Object tcContent : tc.getContent()) {
                        P para = tcContent instanceof JAXBElement
                                ? (P) (((JAXBElement<?>) tcContent).getValue())
                                : (P) tcContent;

                        List<Object> pContent = para.getContent();

                        RPr firstRunPPr = docxCommonService.getFirstRunPrP(para);

                        boolean hasRunIns = docxCommonService.hasRunIns(pContent);
                        boolean hasRunDel = docxCommonService.hasRunDel(pContent);
                        boolean hasRun = docxCommonService.hasRun(pContent);

                        if (hasRun || hasRunDel) {
                            allCellEmpty = false;
                        }

                        if (hasRunIns || hasRunDel) {
                            String text = "";

                            if (isNew) {
                                text = docxCommonService.extractFullParagraphText(pContent).trim();
                            } else {
                                text = docxCommonService.extractFullParagraphTextBeforeModify(pContent).trim();
                            }

                            pContent.clear();

                            R normalRun = docxCommonService.createNormalRun(
                                    text,
                                    firstRunPPr,
                                    false
                            );

                            pContent.add(normalRun);
                        }
                    }
                }

                // 该行遍历完，全部单元格为空，加入待删除列表
                if (allCellEmpty) {
                    toRemoveTrList.add(tr);
                }
            }

            // 旧表格删除新增行，防止空行出现
            if (!isNew && toRemoveTrList.size() > 0) {
                tableRows.removeAll(toRemoveTrList);
            }
        }
    }

    /**
     * 判断一个表格是否是空表格（即所有文本都是空的）
     */
    public boolean tableIsEmpty(Tbl table) {
        List<Object> tableRows = table.getContent();

        if (!CollectionUtils.isEmpty(tableRows)) {
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

                        List<Object> pContent = para.getContent();

                        String pgText = docxCommonService
                                .extractFullParagraphText(pContent)
                                .trim();

                        if (!StringUtils.isEmpty(pgText)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    // 初始化文本标题层级Map
    private Map<Integer, String> initTextTitleMap() {
        Map<Integer, String> titleMap = new HashMap<>();

        for (int i = 1; i <= MAX_TITLE_LEVEL; i++) {
            titleMap.put(i, "");
        }

        return titleMap;
    }

    // 创建一个仿宋、小四、两端对齐，固定行距（22磅），首行缩进2字符的段落P
    public P createStaticPara() {
        P p = factory.createP();
        PPr pPr = factory.createPPr();
        p.setPPr(pPr);

        // 1. 两端对齐
        Jc jc = factory.createJc();
        // jc.setVal(JcEnumeration.BOTH); // 两端对齐
        jc.setVal(JcEnumeration.LEFT); // 左对齐
        pPr.setJc(jc);

        // 首行缩进2字符
        PPrBase.Ind ind = factory.createPPrBaseInd();
        ind.setFirstLineChars(BigInteger.valueOf(200));
        ind.setFirstLine(BigInteger.valueOf(480));

        // 清除左右缩进、悬挂缩进
        ind.setLeftChars(BigInteger.ZERO);
        ind.setHangingChars(BigInteger.ZERO);
        pPr.setInd(ind);

        // 2. 固定行距22磅
        PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
        spacing.setLineRule(STLineSpacingRule.EXACT);

        // 22磅 × 20 = 440
        spacing.setLine(BigInteger.valueOf(440));
        pPr.setSpacing(spacing);

        // 3. 文字样式：仿宋小四
        RPr rPr = factory.createRPr();

        HpsMeasure fontSize = factory.createHpsMeasure();
        fontSize.setVal(BigInteger.valueOf(24)); // 24 半点 = 12pt

        rPr.setSz(fontSize);
        rPr.setSzCs(fontSize);

        RFonts rFonts = factory.createRFonts();
        rFonts.setAscii("FangSong");      // 西文也设为仿宋，保证一致
        rFonts.setHAnsi("FangSong");      // 高 ANSI 字体
        rFonts.setEastAsia("仿宋");       // 东亚字体（中文字体）
        rPr.setRFonts(rFonts);

        // 组装文本run
        R run = factory.createR();
        run.setRPr(rPr);

        Text text = factory.createText();
        text.setValue("STATIC_PARA");

        run.getContent().add(text);
        p.getContent().add(run);

        return p;
    }
}