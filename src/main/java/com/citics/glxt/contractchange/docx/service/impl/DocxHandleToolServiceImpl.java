package com.citics.glxt.contractchange.docx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.ContractAnalysisDetailService;
import com.citics.glxt.contractchange.docx.service.DocxCommonService;
import com.citics.glxt.contractchange.docx.service.DocxHandleToolService;
import com.citics.glxt.contractchange.docx.service.DocxModifyAndLandService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;
import static com.citics.glxt.contractchange.constants.Constants.CONTRACT_ANALYSIS_MODULE_OTHER;
import static com.citics.glxt.contractchange.docx.constants.Constants.*;

@Slf4j
@Service
public class DocxHandleToolServiceImpl implements DocxHandleToolService {

    private static final ObjectFactory factory = new ObjectFactory();

    @Resource
    private DocxCommonService docxCommonService;

    @Resource
    private DocxModifyAndLandService docxModifyAndLandService;

    @Resource
    private ContractAnalysisDetailService landDetailService;

    @Override
    public String jbrKeywordsHandle(DocxAnalysisDTO docxAnalysisDTO) {
        String res = "";

        // 1、获取基础参数并校验
        String jbrFileGetPath = docxAnalysisDTO.getJbrFileGetPath();
        String jbrFileSavePath = docxAnalysisDTO.getJbrFileSavePath();
        Long jbrType = docxAnalysisDTO.getJbrType();
        String jbrKeyWords = docxAnalysisDTO.getJbrKeyWords();

        isFalse(
                StringUtils.isEmpty(jbrFileGetPath),
                "文档获取地址不得为空，请核对！"
        );
        isFalse(
                StringUtils.isEmpty(jbrFileSavePath),
                "文档保存地址不得为空，请核对！"
        );
        isFalse(
                jbrType == null,
                "经办人操作类型不得为空，请核对！"
        );
        isFalse(
                jbrType.equals(JBR_HANDLE_TYPE_ADD)
                        && StringUtils.isEmpty(jbrKeyWords),
                "经办人操作类型不为“新增”时，关键字不得为空，请核对！"
        );

        // 不处理则返回
        if (jbrType.equals(JBR_HANDLE_TYPE_NO)) {
            return res;
        }

        // 核心逻辑
        try {

            // 从文档库加载文件
            WordprocessingMLPackage xmlLoadRes =
                    docxCommonService.getXmlDocument(jbrFileGetPath);

            // 先解析落地
            DocxAnalysisDTO dto = new DocxAnalysisDTO();
            dto.setFileGetPath(jbrFileGetPath);

            Long masterId =
                    docxModifyAndLandService.analyzeAndLand(dto);

            // 各类变量
            MainDocumentPart mainDocumentPart =
                    xmlLoadRes.getMainDocumentPart();

            List<Object> mainDocumentContent =
                    mainDocumentPart.getContent();

            isFalse(
                    mainDocumentContent == null
                            || mainDocumentContent.size() == 0,
                    "文档解析时或因格式问题导致读取为空，请核对或人工处理！"
            );

            NumberingDefinitionsPart ndp =
                    xmlLoadRes
                            .getMainDocumentPart()
                            .getNumberingDefinitionsPart();

            // 若ndp为空，则说明文档中全部为纯文本，无word自带序号
            Numbering numbering = null;

            if (ndp != null) {
                numbering = ndp.getJaxbElement();
            }

            long textCurrentModuleId =
                    CONTRACT_ANALYSIS_MODULE_OTHER;

            ListIterator<Object> mainIterator =
                    mainDocumentContent.listIterator();

            // 处理多个段落实际上属于同一个P的情况
            List<String> pgTextList = new ArrayList<>();

            // 处理多个段落实际上属于同一个P的情况
            List<List<Object>> nodeList = new LinkedList<>();

            // 文本标题层级，记录的键值对是一、 1:一、 2:（二）
            Map<Integer, String> textCurrentTitleMapNum =
                    initTextTitleMap();

            // 文本标题层级，记录的键值对是 1:XXX 2:XXX
            Map<Integer, String> textCurrentTitleMapText =
                    initTextTitleMap();

            String pgText = "";

            PPr ppr = null;
            RPr rpr = null;

            AtomicBoolean jbrIsModifying =
                    new AtomicBoolean(false);

            // 找一下落地明细中经办人数量
            LambdaQueryWrapper<ContractAnalysisDetail> lambda =
                    new LambdaQueryWrapper<>();

            lambda.eq(
                    ContractAnalysisDetail::getMasterId,
                    masterId
            ); // 主对象ID

            lambda.like(
                    ContractAnalysisDetail::getOriginalContent,
                    "经办员（签字） "
            );

            List<ContractAnalysisDetail> landDetailList =
                    landDetailService.list(lambda);

            // 若找到多个报错
            isFalse(
                    landDetailList.size() > 1,
                    "文档处理失败，找到多处“经办员（签字）”，请核对！"
            );

            // 遍历文档
            log.info("开始遍历文档，处理文件...");

            while (mainIterator.hasNext()) {

                Object obj = mainIterator.next();

                // 处理段落
                if (obj instanceof P) {

                    P paragraph = (P) obj;

                    List<Object> pContent =
                            paragraph.getContent();

                    pgTextList =
                            docxCommonService
                                    .extractFullParagraphTextList(
                                            pContent
                                    );

                    nodeList =
                            docxCommonService
                                    .extractFullParagraphNodeList(
                                            pContent
                                    );

                    // 存在多个段落实际上处在同一个P元素中的情况
                    if (nodeList.size() > 1) {

                        docxCommonService.splitAndReplaceParagraph(
                                mainIterator,
                                nodeList,
                                paragraph.getPPr(),
                                docxCommonService
                                        .getFirstRunPrP(paragraph),
                                true
                        );

                        continue;
                    }

                    ppr = paragraph.getPPr();

                    rpr =
                            docxCommonService
                                    .getFirstRunPrP(paragraph);

                    pgText =
                            pgTextList.size() < 1
                                    ? ""
                                    : pgTextList.get(0).trim();

                    // 动态更新：当前模块ID + 标题层级前缀
                    textCurrentModuleId =
                            docxCommonService.getCurrentModule(
                                    pgText,
                                    textCurrentModuleId
                            );

                    docxCommonService.updateTextTitleLevelMap(
                            pgText,
                            textCurrentTitleMapNum,
                            textCurrentTitleMapText
                    );

                    // 有word自带序号前缀，替换为普通文本，并更新标题序号信息
                    if (ppr != null
                            && ppr.getNumPr() != null
                            && numbering != null) {

                        Map<String, String> titleModeMap =
                                docxCommonService
                                        .getTitleModeFromP(
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

                        docxCommonService.updateTextTitleLevelMap(
                                pgText,
                                textCurrentTitleMapNum,
                                textCurrentTitleMapText
                        );
                    }

                    // 删除经办人
                    if (jbrType.equals(JBR_HANDLE_TYPE_DEL)) {

                        if (pgText.startsWith("经办员（签字） ")) {

                            // 查找后面一个段落是否是“日期”
                            Long id =
                                    landDetailList.get(0).getId();

                            LambdaQueryWrapper<ContractAnalysisDetail> lambdaSub =
                                    new LambdaQueryWrapper<>();

                            lambdaSub.eq(
                                    ContractAnalysisDetail::getMasterId,
                                    masterId
                            );

                            lambdaSub.eq(
                                    ContractAnalysisDetail::getId,
                                    id + 1
                            );

                            List<ContractAnalysisDetail> landDetailSubList =
                                    landDetailService.list(lambdaSub);

                            if (landDetailSubList.size() == 1) {

                                boolean equalsNext =
                                        landDetailSubList
                                                .get(0)
                                                .getOriginalContent()
                                                .startsWith("日期");

                                if (equalsNext) {

                                    jbrIsModifying.set(true);

                                    docxCommonService.replaceKeyWordInPara(
                                            paragraph,
                                            pgText,
                                            pgText,
                                            "",
                                            false
                                    );
                                }
                            }
                        }

                        if (pgText.startsWith("日期")
                                && jbrIsModifying.get()) {

                            docxCommonService.replaceKeyWordInPara(
                                    paragraph,
                                    pgText,
                                    pgText,
                                    "",
                                    false
                            );

                            jbrIsModifying.set(false);
                        }
                    }

                    // 新增经办人
                    if (jbrType.equals(JBR_HANDLE_TYPE_ADD)) {

                        // 不存在经办人才新增
                        if (pgText.startsWith(jbrKeyWords)
                                && landDetailList.size() < 1) {

                            P newPara1 =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    paragraph,
                                                    "经办员（签字）： ",
                                                    false
                                            );

                            docxCommonService.addNewParagraph(
                                    mainIterator,
                                    newPara1,
                                    false
                            );

                            P newPara2 =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    paragraph,
                                                    "日期： ",
                                                    false
                                            );

                            docxCommonService.addNewParagraph(
                                    mainIterator,
                                    newPara2,
                                    false
                            );

                            P newPara3 =
                                    docxCommonService
                                            .createParagraphWithSameStyle(
                                                    paragraph,
                                                    " ",
                                                    false
                                            );

                            docxCommonService.addNewParagraph(
                                    mainIterator,
                                    newPara3,
                                    false
                            );
                        }
                    }

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
                }
            }

            // 保存修改后的文档
            docxCommonService.saveModifiedDocument(
                    xmlLoadRes,
                    jbrFileSavePath
            );

        } catch (Exception e) {

            log.error(
                    "文档处理失败，文档获取地址：{}",
                    jbrFileGetPath,
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

    // 初始化文本标题层级Map
    private Map<Integer, String> initTextTitleMap() {

        Map<Integer, String> titleMap =
                new HashMap<>();

        for (int i = 1; i <= MAX_TITLE_LEVEL; i++) {
            titleMap.put(i, "");
        }

        return titleMap;
    }
}
