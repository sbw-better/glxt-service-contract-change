package com.citics.glxt.contractchange.docx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import com.citics.glxt.contractchange.docx.domain.ModifyConfigTableDetail;
import com.citics.glxt.contractchange.docx.domain.ModifyConfiguration;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.xml.bind.JAXBElement;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;
import static com.citics.glxt.contractchange.docx.constants.Constants.*;

@Slf4j
@Service
public class DocxModifyByConfigServiceImpl implements DocxModifyByConfigService {

    private static final ObjectFactory factory = new ObjectFactory();

    @Resource
    private DocxCommonService docxCommonService;

    @Resource
    private ModifyConfigurationService modifyConfigurationService;

    @Resource
    private ModifyConfigTableDetailService modifyConfigTableDetailService;

    @Resource
    private DocxModifyAndLandService docxModifyAndLandService;

    @Resource
    private ContractAnalysisDetailService landDetailService;

    /**
     * 解析文档，修改xml文件，生成新文件
     *
     * @param docxAnalysisDTO 入参
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String analyzeAndGenerate(DocxAnalysisDTO docxAnalysisDTO) {
        String res = "";

        // 1、获取基础参数并校验
        Long operateType = docxAnalysisDTO.getOperateType() == null
                ? OPERATION_GUIDANCE_GENERATE_CONDITION_THREE
                : docxAnalysisDTO.getOperateType();

        Long plan = docxAnalysisDTO.getPlan();
        String fileGetPath = docxAnalysisDTO.getFileGetPath();
        String fileSavePath = docxAnalysisDTO.getFileSavePath();
        String versionCode = docxAnalysisDTO.getVersionCode();

        isFalse(
                null == plan,
                "方案类型不得为空，请核对！"
        );

        isFalse(
                StringUtils.isEmpty(fileGetPath),
                "文档获取地址不得为空，请核对！"
        );

        isFalse(
                StringUtils.isEmpty(fileSavePath),
                "文档保存地址不得为空，请核对！"
        );

        // 2、仅处理【运作指引整改生成合同】的场景
        if (Objects.equals(
                operateType,
                OPERATION_GUIDANCE_GENERATE_CONDITION_THREE)) {

            try {

                // 先解析落地
                Long masterId =
                        docxModifyAndLandService.analyzeAndLand(
                                docxAnalysisDTO
                        );

                // 3、从文档库加载文件
                WordprocessingMLPackage xmlLoadRes =
                        docxCommonService.getXmlDocument(
                                fileGetPath
                        );

                // 4、查询启用的修改配置规则，筛选出启用的、
                // 指定模板类型的、指定方案的，按照修改顺序升序
                log.info("开始获取修改配置信息...");

                // 所有启用的配置
                LambdaQueryWrapper<ModifyConfiguration> lambda =
                        new LambdaQueryWrapper<>();

                lambda.eq(
                                ModifyConfiguration::getIsUsed,
                                CONSTANTS_YES
                        )
                        .eq(
                                ModifyConfiguration::getTemplateType,
                                OPERATION_GUIDANCE_GENERATE_CONDITION_THREE
                        )
                        .eq(
                                ModifyConfiguration::getPlan,
                                plan
                        )
                        .orderByAsc(
                                ModifyConfiguration::getModifyNum
                        );

                List<ModifyConfiguration> modifyConfigList =
                        modifyConfigurationService.list(lambda);

                isFalse(
                        CollectionUtils.isEmpty(modifyConfigList),
                        "未查询到有效的修改配置规则，文件获取地址：" + fileGetPath
                );

                // 所有启用且“段落已存在则不新增”=“否”的配置
                LambdaQueryWrapper<ModifyConfiguration> lambda2 =
                        new LambdaQueryWrapper<>();

                lambda2.eq(
                                ModifyConfiguration::getIsUsed,
                                CONSTANTS_YES
                        )
                        .eq(
                                ModifyConfiguration::getTemplateType,
                                OPERATION_GUIDANCE_GENERATE_CONDITION_THREE
                        )
                        .eq(
                                ModifyConfiguration::getPlan,
                                plan
                        )
                        .eq(
                                ModifyConfiguration::getIsNotSure,
                                CONSTANTS_NO
                        );

                List<ModifyConfiguration> modifyConfigListRemanent =
                        modifyConfigurationService.list(lambda2);

                // 5、核心处理：解析文档+按照配置修改
                res = parseDocumentContent(
                        xmlLoadRes,
                        modifyConfigList,
                        modifyConfigListRemanent,
                        versionCode,
                        masterId,
                        docxAnalysisDTO
                );

                // 6、保存修改后的文档
                docxCommonService.saveModifiedDocument(
                        xmlLoadRes,
                        fileSavePath
                );

            } catch (Exception e) {

                log.error(
                        "文档解析生成失败，文件获取地址：{}",
                        fileGetPath,
                        e
                );

                throw new RuntimeException(
                        e.getMessage(),
                        e
                );
            }
        }

        return res;
    }

    // ------------------------------ 核心业务方法 ------------------------------

    /**
     * 解析文档内容，按配置规则执行修改
     */
    private String parseDocumentContent(
            WordprocessingMLPackage wordMLPackage,
            List<ModifyConfiguration> modifyConfigList,
            List<ModifyConfiguration> modifyConfigListRemanent,
            String versionCode,
            Long masterId,
            DocxAnalysisDTO docxAnalysisDTO) {

        String resStr = "";

        // 1、获取文档正文+初始化变量
        MainDocumentPart mainDocumentPart =
                wordMLPackage.getMainDocumentPart();

        List<Object> mainDocumentContent =
                mainDocumentPart.getContent();

        isFalse(
                mainDocumentContent == null
                        || mainDocumentContent.size() == 0,
                "文档解析时或因格式问题导致读取为空，请手工处理！"
        );

        NumberingDefinitionsPart ndp =
                wordMLPackage
                        .getMainDocumentPart()
                        .getNumberingDefinitionsPart();

        // 若ndp为空，则说明文档中全部为纯文本，无word自带序号
        Numbering numbering = null;

        if (ndp != null) {
            numbering = ndp.getJaxbElement();
        }

        long textCurrentModuleId =
                CONTRACT_ANALYSIS_MODULE_OTHER;

        boolean VERSION_MODIFIED = false;
        // 基金合同版本号是否已经修改

        Map<Integer, String> configTitleMap =
                new HashMap<>(MAX_TITLE_LEVEL);

        Map<String, Map<Integer, List<P>>> titlePathParagraphMap =
                new HashMap<>();
        // 缓存「层级路径-段落列表」，用于统一重排序号

        List<String> titlePathParagraphList =
                new ArrayList<>();
        // 缓存「层级路径-段落列表」，有插入段落的前缀，
        // 真正需要排序的，用于统一重排序号

        List<P> newParagraphList =
                new ArrayList<>();

        Map<Integer, String> textCurrentTitleMapNum =
                initTextTitleMap();
        // 文本标题层级，记录的键值对是--> 1：一、 2：（二）

        Map<Integer, String> textCurrentTitleMapText =
                initTextTitleMap();
        // 文本标题层级，记录的键值对是--> 1：XXX 2：XXX

        // 2、遍历文档，收集信息+执行关键字文本追加
        // （避免新增元素导致索引异常，暂不调整段落序号）
        ListIterator<Object> mainIterator =
                mainDocumentContent.listIterator();

        List<String> pgTextList =
                new ArrayList<>();
        // 处理多个段落实际上属于同一个P的情况

        List<List<Object>> nodeList =
                new LinkedList<>();
        // 处理多个段落实际上属于同一个P的情况

        Map<String, Integer> circleCountMap =
                new HashMap<>();
        // 同个标题层级下，圆圈序号的数量

        String pgText = "";
        String pgTextPre = "";

        PPr ppr = null;
        PPr pprPre = null;

        RPr rpr = null;
        RPr rprPre = null;

        AtomicBoolean taIsModifying =
                new AtomicBoolean(false);

        while (mainIterator.hasNext()) {

            Object obj = mainIterator.next();

            // 2.1、处理段落、文本
            if (obj instanceof P) {

                // 2.1.1、解析段落文本（去重空格）
                // 获取当前段落的完整文本内容，以及获取节点信息（可兼容合并到）
                pprPre = ppr != null
                        ? XmlUtils.deepCopy(ppr)
                        : pprPre;

                rprPre = rpr != null
                        ? XmlUtils.deepCopy(rpr)
                        : rprPre;

                pgTextPre = pgText;

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

                pgText = pgTextList.size() < 1
                        ? ""
                        : pgTextList.get(0).trim();

                // ------------------------------ 处理版本号 ------------------------------

                // 2.1.2、特殊处理基金合同版本号
                // （在判断pgTextList.size()<1之前处理）
                if (!VERSION_MODIFIED
                        && textCurrentModuleId
                        == CONTRACT_ANALYSIS_MODULE_OTHER
                        && !StringUtils.isEmpty(versionCode)) {

                    Pattern patternA =
                            Pattern.compile(
                                    "基金合同(?:[（(]第\\d+版[）)])?|[（(]第\\d+版[）)]"
                            );
                    // 匹配：基金合同 或 基金合同（第3版）或（第5版）

                    Pattern patternB =
                            Pattern.compile(
                                    "基金合同[（(]\\d{4}-\\d+[）)]"
                            );
                    // 匹配：基金合同（2026-1）

                    Pattern patternC =
                            Pattern.compile(
                                    "[（(]\\d{4}-\\d+[）)]"
                            );
                    // 匹配：（2026-1）

                    // 当前段落内容为：基金合同（2026-1）
                    Matcher matcher1 =
                            patternB.matcher(pgText);

                    if (matcher1.find()) {

                        docxCommonService.replaceParagraphText(
                                paragraph,
                                pgText,
                                CONTRACT_VERSION_CODE_STRING_KEYWORD,
                                null,
                                true
                        );
                    }

                    // 前一段落内容为：基金合同 或 基金合同（第3版）或（第5版）
                    Matcher matcher2 =
                            patternA.matcher(pgTextPre);

                    if (matcher2.find()
                            && !pgTextPre.contains(
                            CONTRACT_VERSION_CODE_STRING_KEYWORD
                    )) {

                        // 当前段落内容为：（2026-1）
                        Matcher matcher3 =
                                patternC.matcher(pgText);

                        if (matcher3.find()) {

                            docxCommonService.replaceParagraphText(
                                    paragraph,
                                    pgText,
                                    versionCode,
                                    null,
                                    true
                            );

                            VERSION_MODIFIED = true;
                        }

                        // 当前段落内容为空
                        if (StringUtils.isEmpty(pgText)) {

                            docxCommonService.replaceParagraphText(
                                    paragraph,
                                    pgText,
                                    versionCode,
                                    rprPre,
                                    true
                            );

                            VERSION_MODIFIED = true;
                        }
                    }
                }

                // -----------------------------------------------------------------------

                if (!StringUtils.isEmpty(pgText)) {

                    if (pgText.contains(
                            "基金投资者属于以下情形的，不适用本款关于投资冷静期")) {

                        int yy = 0;
                    }

                    // 2.1.3、动态更新：当前模块ID + 标题层级前缀
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

                    // 有word自带序号前缀，替换为普通文本，
                    // 并更新标题序号信息
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
                                        true
                                );

                        docxCommonService.updateTextTitleLevelMap(
                                pgText,
                                textCurrentTitleMapNum,
                                textCurrentTitleMapText
                        );
                    }

                    // 2.1.4、核心：按照配置规则执行修改
                    executeModifyConfig(
                            mainIterator,
                            paragraph,
                            pgText,
                            textCurrentModuleId,
                            textCurrentTitleMapNum,
                            textCurrentTitleMapText,
                            titlePathParagraphMap,
                            titlePathParagraphList,
                            newParagraphList,
                            configTitleMap,
                            modifyConfigList,
                            modifyConfigListRemanent,
                            masterId
                    );

                    // 2.1.5、核心：自定义规则执行修改，一些写死的逻辑
                    executeModifySelfDefine(
                            mainIterator,
                            paragraph,
                            pgText,
                            textCurrentModuleId,
                            textCurrentTitleMapNum,
                            textCurrentTitleMapText,
                            titlePathParagraphMap,
                            titlePathParagraphList,
                            newParagraphList,
                            masterId,
                            docxAnalysisDTO,
                            taIsModifying
                    );

                    // 2.1.6、缓存当前段落的标题信息
                    // （用于后续统一重排）
                    cacheParagraphTitlePath(
                            paragraph,
                            pgText,
                            textCurrentTitleMapNum,
                            textCurrentModuleId,
                            titlePathParagraphMap,
                            titlePathParagraphList,
                            newParagraphList,
                            false
                    );
                }

            } else {

                // 2.2、处理表格。
                // 初始化表格列表，收集当前节点中的所有Tbl；
                // 遍历找到的表格，执行表格修改逻辑
                List<Tbl> tblList =
                        new ArrayList<>();

                docxCommonService.findAllTblInNode(
                        obj,
                        tblList
                );

                if (!tblList.isEmpty()) {

                    for (Tbl table : tblList) {

                        executeTableModifyConfig(
                                table,
                                textCurrentModuleId,
                                textCurrentTitleMapText,
                                configTitleMap,
                                modifyConfigList,
                                modifyConfigListRemanent
                        );
                    }
                }
            }
        }

        // 存在启用且“段落已存在则不新增”=“否”的配置未被处理
        if (modifyConfigListRemanent.size() > 0) {

            StringBuilder msg =
                    new StringBuilder();

            for (ModifyConfiguration m
                    : modifyConfigListRemanent) {

                String s =
                        !StringUtils.isEmpty(m.getKeyWord())
                                ? m.getKeyWord()
                                : "";

                s = s.length() > 30
                        ? s.substring(0, 30) + "..."
                        : s;

                msg.append("【配置ID：")
                        .append(m.getId())
                        .append("，配置关键字：")
                        .append(s)
                        .append("】");
            }

            resStr =
                    "存在配置未被处理，请核对生成后的文档，配置信息："
                            + msg.toString();
        }

        // 3、所有内容修改完成后，涉及段落修改的，
        // 统一重排全文档的标题序号
        reorderAllTitleNumbers(
                mainDocumentContent,
                titlePathParagraphMap,
                titlePathParagraphList,
                newParagraphList
        );

        return resStr;
    }

    /**
     * 修改配置规则：模块+标题层级+关键字三重匹配
     *
     * @param mainIterator             文档主内容迭代器
     * @param paragraph                段落对象
     * @param pgText                   段落对象的完整文本
     * @param textCurrentModuleId      文本所属模块
     * @param textCurrentTitleMapNum   文本当前的层级标题集
     * @param textCurrentTitleMapText  文本当前的层级标题集
     * @param titlePathParagraphMap    待排序标题集
     * @param titlePathParagraphList   真正待排序标题前缀集
     * @param newParagraphList         新插入段落集
     * @param configTitleMap           配置对象的层级标题集
     * @param modifyConfigList         配置对象列表
     * @param modifyConfigListRemanent 配置对象列表2
     * @param masterId                 文件解析落地主对象ID
     */
    private void executeModifyConfig(ListIterator<Object> mainIterator,
                                     P paragraph,
                                     String pgText,
                                     long textCurrentModuleId,
                                     Map<Integer, String> textCurrentTitleMapNum,
                                     Map<Integer, String> textCurrentTitleMapText,
                                     Map<String, Map<Integer, List<P>>> titlePathParagraphMap,
                                     List<String> titlePathParagraphList,
                                     List<P> newParagraphList,
                                     Map<Integer, String> configTitleMap,
                                     List<ModifyConfiguration> modifyConfigList,
                                     List<ModifyConfiguration> modifyConfigListRemanent,
                                     Long masterId) {

        for (ModifyConfiguration m : modifyConfigList) {

            // 从配置对象中获取定位方式、变更类型、修订类型匹配到几级标题、段落已存在则不新增、关键字
            Long locationType = m.getLocationType();
            Long modifyType = m.getModifyType();
            Long keyWordModifyType = m.getKeyWordModifyType();
            int matchToWhichLevelTitle = m.getMatchToWhichLevelTitle();
            Long isNotSure = m.getIsNotSure();
            String keyWord = m.getKeyWord();
            String keyWordModifyText = m.getKeyWordModifyText(); // 关键字处变更文本内容

            initConfigTitleMap(m, configTitleMap); // 初始化配置标题层级

            // 关键字定位方式
            if (locationType == LOCATION_TYPE_KEYWORD) {

                // 过滤1：模块不匹配则跳过
                if (!Objects.equals(m.getModule(), textCurrentModuleId)) {
                    continue;
                }

                // 过滤2：校验层级匹配
                if (!docxCommonService.isTitleMatched(
                        textCurrentTitleMapText,
                        configTitleMap,
                        matchToWhichLevelTitle)) {
                    continue;
                }

                // 过滤3：关键字不匹配直接跳过
                if (StringUtils.isEmpty(keyWord) || !pgText.contains(keyWord)) {
                    continue;
                }

                log.info(
                        "段落模块、标题层级、关键字均匹配成功，配置记录ID：{}",
                        m.getId()
                );

                // 当前暂时仅处理「关键字新增」类型
                if (Objects.equals(keyWordModifyType, KEYWORDS_MODIFY_TYPE_ADD)
                        && modifyType != null) {

                    // 关键字前/后文本操作
                    if (modifyType == MODIFY_TYPE_TEXT_BEFORE_KEYWORDS_IN
                            || modifyType == MODIFY_TYPE_TEXT_BACK_KEYWORDS_IN) {

                        boolean isBefore = Objects.equals(
                                modifyType,
                                MODIFY_TYPE_TEXT_BEFORE_KEYWORDS_IN
                        );

                        docxCommonService.appendTextToPara(
                                paragraph,
                                pgText,
                                keyWord,
                                keyWordModifyText,
                                isBefore
                        );

                        // 已处理的配置清除掉
                        modifyConfigListRemanent.remove(m);

                        // 关键字前/后新增段落操作，顺延同层级序号
                    } else if (modifyType == MODIFY_TYPE_TEXT_BEFORE_KEYWORDS_OUT_CHANGE
                            || modifyType == MODIFY_TYPE_TEXT_BACK_KEYWORDS_OUT_CHANGE) {

                        boolean isAfter = Objects.equals(
                                modifyType,
                                MODIFY_TYPE_TEXT_BACK_KEYWORDS_OUT_CHANGE
                        );

                        String[] keyWordModifyTextSplit =
                                keyWordModifyText.split(PARAGRAPH_SPLIT_FLAG);

                        String newParaContent =
                                docxCommonService.generateNewParagraphContent(
                                        keyWordModifyTextSplit[0],
                                        textCurrentTitleMapNum,
                                        isAfter
                                );

                        P newPara =
                                docxCommonService.createParagraphWithSameStyle(
                                        paragraph,
                                        newParaContent,
                                        true
                                ); // 完整继承格式

                        docxCommonService.addNewParagraph(
                                mainIterator,
                                newPara,
                                isAfter
                        );

                        // 缓存新插入段落的标题信息（用于全部修改完成后统一重排段落序号）
                        Map<Integer, String> newParaTitleMapNum =
                                new HashMap<>(textCurrentTitleMapNum);

                        Map<Integer, String> newParaTitleMapText =
                                new HashMap<>(textCurrentTitleMapText);

                        docxCommonService.updateTextTitleLevelMap(
                                newParaContent,
                                newParaTitleMapNum,
                                newParaTitleMapText
                        );

                        cacheParagraphTitlePath(
                                newPara,
                                newParaContent,
                                newParaTitleMapNum,
                                textCurrentModuleId,
                                titlePathParagraphMap,
                                titlePathParagraphList,
                                newParagraphList,
                                true
                        );

                        // 对于有子段落的配置（&&next&&分割）额外处理，但子段落不参与排序
                        if (keyWordModifyTextSplit.length > 1) {
                            for (int i = 1; i < keyWordModifyTextSplit.length; i++) {

                                P newParaSub =
                                        docxCommonService.createParagraphWithSameStyle(
                                                paragraph,
                                                keyWordModifyTextSplit[i],
                                                true
                                        ); // 完整继承格式

                                docxCommonService.addNewParagraph(
                                        mainIterator,
                                        newParaSub,
                                        isAfter
                                );
                            }
                        }

                        // 已处理的配置清除掉
                        modifyConfigListRemanent.remove(m);
                    }
                }
            }

            // 章节定位方式
            if (locationType == LOCATION_TYPE_TITLE) {

                // 当前暂时仅处理「新增」类型
                if (Objects.equals(keyWordModifyType, KEYWORDS_MODIFY_TYPE_ADD)
                        && modifyType != null) {

                    // 过滤1：模块不匹配则跳过
                    if (!Objects.equals(m.getModule(), textCurrentModuleId)) {
                        continue;
                    }

                    // 过滤2：校验层级匹配
                    if (!docxCommonService.isTitleMatched(
                            textCurrentTitleMapText,
                            configTitleMap,
                            matchToWhichLevelTitle)) {
                        continue;
                    }

                    // 匹配到N级标题的意思是在N级标题下新增下一级标题
                    // 先找一下落地表中N级标题记录
                    boolean isOk = true;
                    String paraLevelPres = "";
                    ContractAnalysisDetail targetLandDetail = null;

                    for (int i = 1; i <= matchToWhichLevelTitle; i++) {

                        // 获取配置记录中的i级标题文本内容
                        String titleText = configTitleMap.get(i);

                        // 找一下落地明细中是否有该标题
                        // （可能有多个，不排除不同层级下相同标题名称的标题）
                        LambdaQueryWrapper<ContractAnalysisDetail> lambda =
                                new LambdaQueryWrapper<>();

                        lambda.eq(
                                ContractAnalysisDetail::getMasterId,
                                masterId
                        ); // 主对象ID

                        lambda.eq(
                                ContractAnalysisDetail::getModule,
                                textCurrentModuleId
                        ); // 模块

                        lambda.eq(
                                ContractAnalysisDetail::getIsBelongTitle,
                                CONSTANTS_YES
                        ); // 是否是标题

                        lambda.eq(
                                ContractAnalysisDetail::getTitleLevel,
                                String.valueOf(i)
                        ); // 标题级别

                        lambda.like(
                                ContractAnalysisDetail::getOriginalContent,
                                titleText
                        ); // 标题内容

                        if (i > 1) {
                            lambda.likeRight(
                                    ContractAnalysisDetail::getParaLevelInfo,
                                    paraLevelPres
                            ); // 标题前缀信息
                        }

                        List<ContractAnalysisDetail> landDetailList =
                                landDetailService.list(lambda);

                        if (landDetailList.size() != 1) {
                            isOk = false;
                            break;
                        }

                        paraLevelPres =
                                landDetailList.get(0).getParaLevelInfo();

                        if (i == matchToWhichLevelTitle) {
                            targetLandDetail = landDetailList.get(0);
                        }
                    }

                    if (!isOk || targetLandDetail == null) {
                        log.error(
                                "在落地详情表中，未找到配置记录的标题信息，配置记录id：{}",
                                m.getId()
                        );
                        continue;
                    }

                    // 获得目标标题段落以及标题层级信息，
                    // 其下的子段落的层级信息都会包含该前缀，例如：一、（二）3、
                    String targetParaLevelInfo =
                            targetLandDetail.getParaLevelInfo();

                    // 看配置为“已存在则不插入”的段落是否已存在，是否要插入
                    if (isNotSure == CONSTANTS_YES) {

                        LambdaQueryWrapper<ContractAnalysisDetail> lambdaSub =
                                new LambdaQueryWrapper<>();

                        lambdaSub.eq(
                                ContractAnalysisDetail::getMasterId,
                                masterId
                        ); // 主对象ID

                        lambdaSub.eq(
                                ContractAnalysisDetail::getModule,
                                textCurrentModuleId
                        ); // 模块

                        lambdaSub.eq(
                                ContractAnalysisDetail::getIsBelongTitle,
                                CONSTANTS_YES
                        ); // 是否是标题

                        lambdaSub.eq(
                                ContractAnalysisDetail::getTitleLevel,
                                String.valueOf(matchToWhichLevelTitle + 1)
                        ); // 标题级别

                        lambdaSub.likeRight(
                                ContractAnalysisDetail::getParaLevelInfo,
                                targetParaLevelInfo
                        ); // 标题前缀

                        lambdaSub.like(
                                ContractAnalysisDetail::getOriginalContent,
                                keyWord
                        ); // 不确定是否添加的段落的标题内容

                        int count =
                                landDetailService.count(lambdaSub);

                        // 段落已存在则不处理
                        if (count > 0) {
                            continue;
                        }
                    }

                    // 目前暂时仅处理在同级别标题最后位置新增段落
                    if (modifyType == MODIFY_TYPE_TEXT_LAST_CHANGE) {

                        // 找到母级标题层级下所有段落，不限标题层级
                        LambdaQueryWrapper<ContractAnalysisDetail> lambdaTmp =
                                new LambdaQueryWrapper<>();

                        lambdaTmp.eq(
                                ContractAnalysisDetail::getMasterId,
                                masterId
                        ); // 主对象ID

                        lambdaTmp.eq(
                                ContractAnalysisDetail::getModule,
                                textCurrentModuleId
                        ); // 模块

                        lambdaTmp.likeRight(
                                ContractAnalysisDetail::getParaLevelInfo,
                                targetParaLevelInfo
                        ); // 标题前缀

                        lambdaTmp.orderByDesc(
                                ContractAnalysisDetail::getId
                        );

                        List<ContractAnalysisDetail> paraList =
                                landDetailService.list(lambdaTmp);

                        // 暂时不处理标题下无段落的情况，
                        // 即该层级下仅有标题所在本身段落一个段落
                        if (paraList.size() > 1) {

                            ContractAnalysisDetail lastDetail =
                                    paraList.get(0);

                            String lastDetailText =
                                    lastDetail.getOriginalContent();

                            if (pgText.equals(lastDetailText)) {

                                String[] keyWordModifyTextSplit =
                                        keyWordModifyText.split(
                                                PARAGRAPH_SPLIT_FLAG
                                        );

                                String newParaContent =
                                        docxCommonService
                                                .generateTitlePrefixByLevelAndNum(
                                                        matchToWhichLevelTitle + 1,
                                                        1
                                                )
                                                + keyWordModifyTextSplit[0];

                                P newPara =
                                        docxCommonService
                                                .createParagraphWithSameStyle(
                                                        paragraph,
                                                        newParaContent,
                                                        true
                                                ); // 完整继承格式

                                docxCommonService.addNewParagraph(
                                        mainIterator,
                                        newPara,
                                        true
                                );

                                // 缓存新插入段落的标题信息
                                // （用于全部修改完成后统一重排段落序号）
                                Map<Integer, String> newParaTitleMapNum =
                                        new HashMap<>(
                                                textCurrentTitleMapNum
                                        );

                                Map<Integer, String> newParaTitleMapText =
                                        new HashMap<>(
                                                textCurrentTitleMapText
                                        );

                                docxCommonService.updateTextTitleLevelMap(
                                        newParaContent,
                                        newParaTitleMapNum,
                                        newParaTitleMapText
                                );

                                cacheParagraphTitlePath(
                                        newPara,
                                        newParaContent,
                                        newParaTitleMapNum,
                                        textCurrentModuleId,
                                        titlePathParagraphMap,
                                        titlePathParagraphList,
                                        newParagraphList,
                                        true
                                );

                                // 对于有子段落的配置（&&next&&分割）额外处理，
                                // 但子段落不参与排序
                                if (keyWordModifyTextSplit.length > 1) {
                                    for (int i = 1;
                                         i < keyWordModifyTextSplit.length;
                                         i++) {

                                        P newParaSub =
                                                docxCommonService
                                                        .createParagraphWithSameStyle(
                                                                paragraph,
                                                                keyWordModifyTextSplit[i],
                                                                true
                                                        ); // 完整继承格式

                                        docxCommonService.addNewParagraph(
                                                mainIterator,
                                                newParaSub,
                                                true
                                        );
                                    }
                                }

                                // 已处理的配置清除掉
                                modifyConfigListRemanent.remove(m);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 修改配置规则：模块+标题层级+关键字三重匹配
     *
     * @param mainIterator            文档主内容迭代器
     * @param paragraph               段落对象
     * @param pgText                  段落对象的完整文本
     * @param textCurrentModuleId     文本所属模块
     * @param textCurrentTitleMapNum  文本当前的层级标题集
     * @param textCurrentTitleMapText 文本当前的层级标题集
     * @param titlePathParagraphMap   待排序标题集
     * @param titlePathParagraphList  真正待排序标题前缀集
     * @param newParagraphList        新插入段落集
     * @param masterId                文件解析落地主对象ID
     * @param docxAnalysisDTO         入参
     * @param taIsModifying           ta账户确认信息正在修改中
     */
    private void executeModifySelfDefine(
            ListIterator<Object> mainIterator,
            P paragraph,
            String pgText,
            long textCurrentModuleId,
            Map<Integer, String> textCurrentTitleMapNum,
            Map<Integer, String> textCurrentTitleMapText,
            Map<String, Map<Integer, List<P>>> titlePathParagraphMap,
            List<String> titlePathParagraphList,
            List<P> newParagraphList,
            Long masterId,
            DocxAnalysisDTO docxAnalysisDTO,
            AtomicBoolean taIsModifying) {

        // 获取参数
        Integer managerTaIsConfirm =
                docxAnalysisDTO.getManagerTaIsConfirm();

        Boolean isZhongJin =
                docxAnalysisDTO.getIsZhongJin();

        // 一、基金合同释义部分
        if (textCurrentModuleId == CONTRACT_ANALYSIS_MODULE_MAIN
                && textCurrentTitleMapText
                .get(1)
                .replaceAll("\\s", "")
                .equals("释义")) {

            // 是否含关键字“在本合同中，除上下文另有规定外，下列用语应具有如下含义”
            String keyWord =
                    "在本合同中，除上下文另有规定外，下列用语应具有如下含义";

            boolean containsKeywords =
                    pgText.contains(keyWord);

            if (containsKeywords) {

                // 是否含有A部分
                String contentA =
                        "本释义部分提及的投资品种仅为对该品种所适用的资产范围、资产分类和计算口径的说明，" +
                                "并不代表本基金实际可以投资该种范围或品种，本基金实际可投资的范围以本合同约定的“投资范围”为准。";

                boolean containsA =
                        pgText.contains(contentA);

                // 是否含有B部分
                String contentB =
                        "若因法律法规的制定或修改、金融监管部门更新解释或说明等原因导致下述释义与届时" +
                                "有效的法律法规规定或金融监管部门解释说明不一致的，应当以届时有效的法律法规或金融监管部门解释说明为准，" +
                                "由基金管理人按本合同约定的方式之一通知基金委托人和基金托管人即可，无需适用或履行合同变更程序。";

                boolean containsB =
                        pgText.contains(contentB);

                // 情况1：两部分都没有
                if (!containsA && !containsB) {

                    docxCommonService.appendTextToPara(
                            paragraph,
                            pgText,
                            keyWord,
                            "（" + contentA + contentB + "）",
                            false
                    );
                }

                // 情况2：有A无B
                if (containsA && !containsB) {

                    docxCommonService.appendTextToPara(
                            paragraph,
                            pgText,
                            contentA,
                            contentB,
                            false
                    );
                }

                // 情况3：无A有B
                if (!containsA && containsB) {

                    docxCommonService.appendTextToPara(
                            paragraph,
                            pgText,
                            contentB,
                            contentA,
                            true
                    );
                }
            }
        }

        // 二、基金的投资-投资限制 章节固定删除内容（中金公司的除外）
        if (textCurrentModuleId == CONTRACT_ANALYSIS_MODULE_MAIN
                && textCurrentTitleMapText
                .get(1)
                .replaceAll("\\s", "")
                .equals("基金的投资")
                && textCurrentTitleMapText
                .get(2)
                .replaceAll("\\s", "")
                .equals("投资限制")
                && (isZhongJin == null
                || (isZhongJin != null
                && isZhongJin.equals(CONSTANTS_NO)))) {

            // 待删除的文本内容
            Pattern pattern = Pattern.compile(
                    "基金管理人自本基金成立之日起.*?符合上款约定。" +
                            "|" +
                            "基金托管人对本基金的投资的监督自本基金成立之日起开始。" +
                            "|" +
                            "基金管理人自本基金成立之日使本基金的投资组合比例符合上款约定。"
            );

            Matcher matcher =
                    pattern.matcher(pgText);

            List<String> matcherList =
                    new ArrayList<>();

            // 提取所有匹配到的完整句子
            while (matcher.find()) {
                matcherList.add(matcher.group());
            }

            if (matcherList.size() > 0) {

                docxCommonService.deleteKeyWordInParaBatch(
                        paragraph,
                        pgText,
                        matcherList,
                        true
                );
            }
        }

        // 三、全文“合同法”改为“民法典”
        if (pgText.contains("合同法")) {

            docxCommonService.replaceKeyWordInPara(
                    paragraph,
                    pgText,
                    "合同法",
                    "民法典",
                    true
            );
        }

        // 四、全文删除“吴俊文”所在行的表述
        if (pgText.contains("吴俊文")) {

            docxCommonService.replaceKeyWordInPara(
                    paragraph,
                    pgText,
                    pgText,
                    "",
                    true
            );
        }

        // 五、TA账户的更新逻辑
        if (managerTaIsConfirm != null
                && (managerTaIsConfirm == CONSTANTS_YES
                || managerTaIsConfirm == CONSTANTS_NO)
                && pgText.equals("注册登记机构的资金清算专用账户为：")) {

            // 找一下落地明细中
            LambdaQueryWrapper<ContractAnalysisDetail> lambda =
                    new LambdaQueryWrapper<>();

            lambda.eq(
                    ContractAnalysisDetail::getMasterId,
                    masterId
            ); // 主对象ID

            if (!StringUtils.isEmpty(
                    textCurrentTitleMapNum.get(1))) {

                lambda.eq(
                        ContractAnalysisDetail::getFirstTitle,
                        textCurrentTitleMapNum.get(1)
                );
            }

            if (!StringUtils.isEmpty(
                    textCurrentTitleMapNum.get(2))) {

                lambda.eq(
                        ContractAnalysisDetail::getSecondTitle,
                        textCurrentTitleMapNum.get(2)
                );
            }

            if (!StringUtils.isEmpty(
                    textCurrentTitleMapNum.get(3))) {

                lambda.eq(
                        ContractAnalysisDetail::getThirdTitle,
                        textCurrentTitleMapNum.get(3)
                );
            }

            if (!StringUtils.isEmpty(
                    textCurrentTitleMapNum.get(4))) {

                lambda.eq(
                        ContractAnalysisDetail::getFourthTitle,
                        textCurrentTitleMapNum.get(4)
                );
            }

            if (!StringUtils.isEmpty(
                    textCurrentTitleMapNum.get(5))) {

                lambda.eq(
                        ContractAnalysisDetail::getFifthTitle,
                        textCurrentTitleMapNum.get(5)
                );
            }

            lambda.like(
                    ContractAnalysisDetail::getOriginalContent,
                    pgText
            );

            List<ContractAnalysisDetail> landDetailList =
                    landDetailService.list(lambda);

            // 若相同标题下找到多个或未找到 暂不处理
            if (landDetailList.size() == 1) {

                Long id =
                        landDetailList.get(0).getId();

                LambdaQueryWrapper<ContractAnalysisDetail> lambdaSub =
                        new LambdaQueryWrapper<>();

                lambdaSub.eq(
                        ContractAnalysisDetail::getMasterId,
                        masterId
                );

                lambdaSub.ge(
                        ContractAnalysisDetail::getId,
                        id + 1
                );

                lambdaSub.le(
                        ContractAnalysisDetail::getId,
                        id + 4
                );

                lambdaSub.orderByAsc(
                        ContractAnalysisDetail::getId
                );

                List<ContractAnalysisDetail> landDetailSubList =
                        landDetailService.list(lambdaSub);

                if (landDetailSubList.size() == 4) {

                    boolean equalsA =
                            landDetailSubList
                                    .get(0)
                                    .getOriginalContent()
                                    .equals("开户银行：中信银行北京瑞城中心支行");

                    boolean equalsB =
                            landDetailSubList
                                    .get(1)
                                    .getOriginalContent()
                                    .equals("账号：8110701013200381180");

                    boolean equalsC =
                            landDetailSubList
                                    .get(2)
                                    .getOriginalContent()
                                    .equals("账户名称：中信中证投资服务有限责任公司注册登记专户");

                    boolean equalsD =
                            landDetailSubList
                                    .get(3)
                                    .getOriginalContent()
                                    .equals("大额支付号：302100011681");

                    if (equalsA
                            && equalsB
                            && equalsC
                            && equalsD) {

                        taIsModifying.set(true);

                        // 已确认则删除，未确认则替换
                        if (managerTaIsConfirm == CONSTANTS_YES) {

                            docxCommonService.deleteKeyWordInParaOne(
                                    paragraph,
                                    pgText,
                                    pgText,
                                    true
                            );
                        }
                    }
                }
            }
        }

        if (pgText.equals("开户银行：中信银行北京瑞城中心支行")
                && taIsModifying.get()
                && managerTaIsConfirm != null) {

            // 已确认则删除，未确认则替换
            if (managerTaIsConfirm == CONSTANTS_YES) {

                docxCommonService.deleteKeyWordInParaOne(
                        paragraph,
                        pgText,
                        pgText,
                        true
                );
            }

            if (managerTaIsConfirm == CONSTANTS_NO) {

                docxCommonService.replaceKeyWordInPara(
                        paragraph,
                        pgText,
                        pgText,
                        "开户银行：中国建设银行股份有限公司深圳宝城支行",
                        true
                );
            }
        }

        if (pgText.equals("账号：8110701013200381180")
                && taIsModifying.get()
                && managerTaIsConfirm != null) {

            // 已确认则删除，未确认则替换
            if (managerTaIsConfirm == CONSTANTS_YES) {

                docxCommonService.deleteKeyWordInParaOne(
                        paragraph,
                        pgText,
                        pgText,
                        true
                );
            }

            if (managerTaIsConfirm == CONSTANTS_NO) {

                docxCommonService.replaceKeyWordInPara(
                        paragraph,
                        pgText,
                        pgText,
                        "账号：44250100001100010763",
                        true
                );
            }
        }

        if (pgText.equals("账户名称：中信中证投资服务有限责任公司注册登记专户")
                && taIsModifying.get()
                && managerTaIsConfirm != null) {

            // 已确认则删除，未确认则替换
            if (managerTaIsConfirm == CONSTANTS_YES) {

                docxCommonService.deleteKeyWordInParaOne(
                        paragraph,
                        pgText,
                        pgText,
                        true
                );
            }

            if (managerTaIsConfirm == CONSTANTS_NO) {

                docxCommonService.replaceKeyWordInPara(
                        paragraph,
                        pgText,
                        pgText,
                        "账户名称：中信中证投资服务有限责任公司注册登记专户",
                        true
                );
            }
        }

        if (pgText.equals("大额支付号：302100011681")
                && taIsModifying.get()
                && managerTaIsConfirm != null) {

            // 已确认则删除，未确认则替换
            if (managerTaIsConfirm == CONSTANTS_YES) {

                docxCommonService.deleteKeyWordInParaOne(
                        paragraph,
                        pgText,
                        pgText,
                        true
                );
            }

            if (managerTaIsConfirm == CONSTANTS_NO) {
                docxCommonService.replaceKeyWordInPara(
                        paragraph,
                        pgText,
                        pgText,
                        "大额支付号：105584000739",
                        true
                );
            }

            taIsModifying.set(false);
        }

        // 六、“（本页无正文xxx”前分页
        if (pgText.startsWith("（本页无正文")
                || pgText.startsWith("附件2：授权文件")) {

            List<Object> paragraphContent = paragraph.getContent();

            boolean hasPage =
                    docxCommonService.hasPage(paragraphContent);

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

        // 七、乙方（基金管理人）段落红色字体、斜体问题修复
        if (pgText.startsWith("乙方（基金管理人）")
                || pgText.startsWith("乙方(基金管理人)")) {

            List<Object> pContent = paragraph.getContent();

            // 遍历段落内所有子节点
            for (Object obj : pContent) {

                if (!(obj instanceof R)) {
                    continue;
                }

                R run = (R) obj;
                RPr rPr = run.getRPr();

                if (rPr == null) {
                    continue;
                }

                // 清除斜体 <w:i/>
                rPr.setI(null);

                // 清除字体颜色 <w:color/>
                rPr.setColor(null);
            }
        }
    }

    /**
     * 执行表格相关的修改配置（插入行）
     */
    private void executeTableModifyConfig(
            Tbl table,
            long textCurrentModuleId,
            Map<Integer, String> textCurrentTitleMapText,
            Map<Integer, String> configTitleMap,
            List<ModifyConfiguration> modifyConfigList,
            List<ModifyConfiguration> modifyConfigListRemanent) {

        for (ModifyConfiguration m : modifyConfigList) {

            Long locationType = m.getLocationType();

            // 关键字定位方式
            if (locationType == LOCATION_TYPE_KEYWORD) {

                // 过滤1：仅处理关键字后表格插入行操作
                if (!Objects.equals(
                        m.getModifyType(),
                        MODIFY_TYPE_TABLE_BACK_KEYWORDS)) {
                    continue;
                }

                // 过滤2：模块不匹配则跳过
                if (!Objects.equals(
                        m.getModule(),
                        textCurrentModuleId)) {
                    continue;
                }

                // 过滤3：初始化配置标题层级+校验层级匹配
                initConfigTitleMap(m, configTitleMap);

                if (!docxCommonService.isTitleMatched(
                        textCurrentTitleMapText,
                        configTitleMap,
                        m.getMatchToWhichLevelTitle())) {
                    continue;
                }

                // 过滤4：关键字为空则跳过
                String keyWord = m.getKeyWord();

                if (StringUtils.isEmpty(keyWord)) {
                    log.warn(
                            "表格行插入配置关键字为空，跳过配置ID：{}",
                            m.getId()
                    );
                    continue;
                }

                log.info(
                        "表格模块、标题层级、操作类型均匹配成功，开始处理表格行插入，配置记录ID：{}",
                        m.getId()
                );

                // 1、获取配置对象的表格明细数据，以及插入行数
                LambdaQueryWrapper<ModifyConfigTableDetail> lambda =
                        new LambdaQueryWrapper<>();

                lambda.eq(
                        ModifyConfigTableDetail::getMasterId,
                        m.getId()
                );

                List<ModifyConfigTableDetail> tableDetailList =
                        modifyConfigTableDetailService.list(lambda);

                if (CollectionUtils.isEmpty(tableDetailList)) {
                    continue;
                }

                long insertRowNum = -1;

                for (ModifyConfigTableDetail t : tableDetailList) {
                    insertRowNum = Math.max(
                            insertRowNum,
                            t.getRowNumber()
                    );
                }

                // 2、查找表格中匹配关键字的行
                List<Object> tableRows = table.getContent();

                if (CollectionUtils.isEmpty(tableRows)) {

                    log.warn(
                            "表格无行数据，跳过配置ID：{}",
                            m.getId()
                    );

                    continue;
                }

                // 3、遍历表格行，找到匹配关键字的行
                for (int i = 0; i < tableRows.size(); i++) {

                    Object tmp = tableRows.get(i);

                    Tr tr = tmp instanceof JAXBElement
                            ? (Tr) (((JAXBElement<?>) tmp).getValue())
                            : (Tr) tmp;

                    String rowText =
                            docxCommonService.extractTableRowText(tr);

                    if (rowText.contains(keyWord)) {

                        // 4、插入新行
                        docxCommonService.insertRowsBelow(
                                table,
                                tr,
                                i,
                                insertRowNum,
                                tableDetailList
                        );

                        // 已处理的配置清除掉
                        modifyConfigListRemanent.remove(m);

                        break;
                        // 只处理第一个匹配的行，如需多行匹配可移除break
                    }
                }
            }
        }
    }


// ------------------------------ 辅助方法 ------------------------------

    // 初始化文本标题层级Map
    private Map<Integer, String> initTextTitleMap() {

        Map<Integer, String> titleMap = new HashMap<>();

        for (int i = 1; i <= MAX_TITLE_LEVEL; i++) {
            titleMap.put(i, "");
        }

        return titleMap;
    }

    // 初始化配置记录的每层标题Map
    private void initConfigTitleMap(
            ModifyConfiguration m,
            Map<Integer, String> configTitleMap) {

        configTitleMap.put(
                1,
                StringUtils.isEmpty(m.getFirstTitle())
                        ? ""
                        : m.getFirstTitle()
        );

        configTitleMap.put(
                2,
                StringUtils.isEmpty(m.getSecondTitle())
                        ? ""
                        : m.getSecondTitle()
        );

        configTitleMap.put(
                3,
                StringUtils.isEmpty(m.getThirdTitle())
                        ? ""
                        : m.getThirdTitle()
        );

        configTitleMap.put(
                4,
                StringUtils.isEmpty(m.getFourthTitle())
                        ? ""
                        : m.getFourthTitle()
        );

        configTitleMap.put(
                5,
                StringUtils.isEmpty(m.getFifthTitle())
                        ? ""
                        : m.getFifthTitle()
        );
    }

    /**
     * 缓存段落的标题路径信息（用于后续统一重排）
     *
     * @param paragraph              段落对象
     * @param pgText                 段落文本
     * @param titleMap               段落的标题层级Map
     * @param textCurrentModuleId    段落的模块id
     * @param titlePathParagraphMap  待排序段落集合
     * @param titlePathParagraphList 真正待排序段落前缀集合
     * @param newParagraphList       新段落集合
     * @param isNewPara              是否是新增段落
     */
    private void cacheParagraphTitlePath(
            P paragraph,
            String pgText,
            Map<Integer, String> titleMap,
            long textCurrentModuleId,
            Map<String, Map<Integer, List<P>>> titlePathParagraphMap,
            List<String> titlePathParagraphList,
            List<P> newParagraphList,
            boolean isNewPara) {

        // 无标题部分（如“风险揭示书”）和普通文本：在标题层级内但无前缀序号的
        int validLevel =
                docxCommonService.getCurrentValidTitleLevel(titleMap);

        String preNum =
                docxCommonService.extractPrefixByLevel(
                        pgText,
                        validLevel
                );

        if (validLevel <= 0
                || StringUtils.isEmpty(preNum)) {
            return;
        }

        // 生成前置层级路径
        StringBuilder prefixPath =
                new StringBuilder();

        prefixPath.append("Module:")
                .append(textCurrentModuleId)
                .append(",Titles:");

        if (validLevel == 1) {

            prefixPath.append("NULL");

        } else {

            for (int i = 1; i < validLevel; i++) {

                String prefix =
                        titleMap.getOrDefault(i, "");

                if (!StringUtils.isEmpty(prefix)) {
                    prefixPath.append(prefix).append("|");
                }
            }
        }

        String pathKey =
                prefixPath.toString();

        if (isNewPara) {
            titlePathParagraphList.add(pathKey);
            newParagraphList.add(paragraph);
        }

        // 按「路径+层级」缓存段落
        Map<Integer, List<P>> levelParagraphMap =
                titlePathParagraphMap.computeIfAbsent(
                        pathKey,
                        k -> new HashMap<>()
                );

        List<P> paragraphList =
                levelParagraphMap.computeIfAbsent(
                        validLevel,
                        k -> new ArrayList<>()
                );

        paragraphList.add(paragraph);
    }

    /**
     * 统一重排全文档的标题序号（核心方法）
     */
    private void reorderAllTitleNumbers(
            List<Object> mainDocumentContent,
            Map<String, Map<Integer, List<P>>> titlePathParagraphMap,
            List<String> titlePathParagraphList,
            List<P> newParagraphList) {

        if (titlePathParagraphList == null
                || titlePathParagraphList.size() == 0
                || titlePathParagraphMap == null
                || titlePathParagraphMap.size() == 0) {

            return;
        }

        log.info("开始重排标题序号...");

        // 遍历每个「前置层级路径」
        for (Map.Entry<String, Map<Integer, List<P>>> pathEntry
                : titlePathParagraphMap.entrySet()) {

            String pathKey =
                    pathEntry.getKey();

            // 只对有新段落插入的前缀层级下重排
            if (!titlePathParagraphList.contains(pathKey)) {
                continue;
            }

            Map<Integer, List<P>> levelParagraphMap =
                    pathEntry.getValue();

            // 按层级从小到大重排（1级→2级→3级→4级→5级）
            for (int level = 1;
                 level <= MAX_TITLE_LEVEL;
                 level++) {

                List<P> paragraphList =
                        levelParagraphMap.get(level);

                if (CollectionUtils.isEmpty(paragraphList)) {
                    continue;
                }

                // 按文档中的顺序重新排序段落
                // （确保序号按文档顺序递增）
                paragraphList.sort((p1, p2) -> {

                    int idx1 =
                            mainDocumentContent.indexOf(p1);

                    int idx2 =
                            mainDocumentContent.indexOf(p2);

                    return Integer.compare(idx1, idx2);
                });

                // 逐段落重排序号（从1开始递增）
                for (int i = 0;
                     i < paragraphList.size();
                     i++) {

                    P para =
                            paragraphList.get(i);

                    List<Object> paraContent =
                            para.getContent();

                    // 获取待排序段落的全文和全部Run/RunIns元素
                    String paraText =
                            docxCommonService
                                    .extractFullParagraphText(
                                            paraContent
                                    );

                    if (StringUtils.isEmpty(paraText)) {
                        continue;
                    }

                    // 通过正则提取指定层级的旧前缀
                    String oldPrefix =
                            docxCommonService.extractPrefixByLevel(
                                    paraText,
                                    level
                            );

                    if (StringUtils.isEmpty(oldPrefix)) {

                        log.debug(
                                "段落文本{}未匹配到{}级前缀，跳过重排",
                                paraText.substring(
                                        0,
                                        Math.min(
                                                paraText.length(),
                                                20
                                        )
                                ),
                                level
                        );

                        continue;
                    }

                    // 生成新前缀（i+1为新序号）
                    String newPrefix =
                            docxCommonService
                                    .generateTitlePrefixByLevelAndNum(
                                            level,
                                            i + 1
                                    );

                    // 新旧序号不一致则替换序号
                    if (!newPrefix.equals(oldPrefix)) {

                        docxCommonService.replaceTitlePrefixOnly(
                                para,
                                paraText,
                                oldPrefix,
                                newPrefix,
                                newParagraphList.contains(para)
                        );
                    }
                }
            }
        }
    }
}


