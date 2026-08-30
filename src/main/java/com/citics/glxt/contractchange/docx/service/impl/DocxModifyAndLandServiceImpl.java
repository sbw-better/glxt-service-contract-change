package com.citics.glxt.contractchange.docx.service.impl;

import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisMain;
import com.citics.glxt.contractchange.docx.mapper.ContractAnalysisDetailMapper;
import com.citics.glxt.contractchange.docx.mapper.ContractAnalysisMainMapper;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.ContractAnalysisDetailService;
import com.citics.glxt.contractchange.docx.service.DocxCommonService;
import com.citics.glxt.contractchange.docx.service.DocxModifyAndLandService;
import io.micrometer.core.instrument.util.StringUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.RPr;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

import static com.baomidou.mybatisplus.core.toolkit.Assert.isFalse;
import static com.citics.glxt.common.constants.CommonConstants.CONSTANTS_NO;
import static com.citics.glxt.common.constants.CommonConstants.CONSTANTS_YES;
import static com.citics.glxt.contractchange.docx.constants.Constants.*;

@Slf4j
@Service
public class DocxModifyAndLandServiceImpl implements DocxModifyAndLandService {

    @Resource(name = "taskExecutor")
    private AsyncTaskExecutor taskExecutor;

    @Resource
    private DocxCommonService docxCommonService;

    @Resource
    private ContractAnalysisDetailService detailService;

    @Resource
    private ContractAnalysisMainMapper contractAnalysisMainMapper;

    @Resource
    private ContractAnalysisDetailMapper contractAnalysisDetailMapper;

    @Override
    public Long analyzeAndLand(DocxAnalysisDTO docxAnalysisDTO) {
        // 获取基础参数并校验
        String fileGetPath = docxAnalysisDTO.getFileGetPath();
        isFalse(StringUtils.isEmpty(fileGetPath), "文档获取地址不得为空，请核对！");
        // 从文档库加载文件
        WordprocessingMLPackage xmlLoadRes = docxCommonService.getXmlDocument(fileGetPath);
        log.info("开始处理文件落地信息...");
        // 各类变量
        MainDocumentPart mainDocumentPart = xmlLoadRes.getMainDocumentPart();
        List<Object> mainDocumentContent = mainDocumentPart.getContent();

        isFalse( mainDocumentContent == null || mainDocumentContent.size() == 0, "文档解析时或因格式问题读取为空，请手工处理！");
        NumberingDefinitionsPart ndp = xmlLoadRes.getMainDocumentPart().getNumberingDefinitionsPart();
        // 若ndp为空，则说明文档中全部为纯文本，无word自带序号
        Numbering numbering = null;
        if (ndp != null) {
            numbering = ndp.getJaxbElement();
        }
        long textCurrentModuleId = CONTRACT_ANALYSIS_MODULE_OTHER;
        Map<Integer, String> textCurrentTitleMapNum = initTextTitleMap(); //文本标题层级，记录的键值对--> 1: 一、 2:（二）
        ListIterator<Object> mainIterator = mainDocumentContent.listIterator();
        List<String> pgTextList = new ArrayList<>();
        String pgText = "";

        // 落地解析主表
        ContractAnalysisMain mainObj = new ContractAnalysisMain();
        mainObj.setType(OPERATION_GUIDANCE_GENERATE_CONDITION_THREE);
        mainObj.setFileGetPath(fileGetPath);
        mainObj.setAnalysisTime(new Date());
        int insert = contractAnalysisMainMapper.insert(mainObj);
        if (insert <= 0) {
            log.error("文档解析主表落表失败");
            throw new RuntimeException();
        }
        Long mainObjId = mainObj.getId();
        List<ContractAnalysisDetail> detailList = new ArrayList<>();
        // 遍历文档，文档解析落地
        while (mainIterator.hasNext()) {
            Object obj = mainIterator.next();
            if (obj instanceof P) {
                P paragraph = (P) obj;
                List<Object> pContent = paragraph.getContent();
                PPr ppr = paragraph.getPPr();
                RPr rpr = docxCommonService.getFirstRunPrP(paragraph);
                pgTextList = docxCommonService.extractFullParagraphTextList(pContent);
                for (int sn=0; sn< pgTextList.size(); sn++) {
                    pgText = pgTextList.get(sn);
                    if (!StringUtils.isEmpty(pgText)) {
                        //动态更新：当前模块ID + 标题层级前缀
                        textCurrentModuleId = docxCommonService.getCurrentModule(pgText, textCurrentModuleId);
                        docxCommonService.updateTextTitleLevelMap(pgText, textCurrentTitleMapNum, null);
                        //有word自带序号前缀，替换为普通文本，并更新标题序号信息（仅针对第一个文本）
                        if (sn ==0 && ppr != null && numbering!=null) {
                            Map<String, String> titleModeMap = docxCommonService.getTitleModeFromP(numbering, ppr);
                            pgText = docxCommonService.handleTitleMode(paragraph, ppr, rpr, pgText, titleModeMap, textCurrentTitleMapNum, true);
                            docxCommonService.updateTextTitleLevelMap(pgText, textCurrentTitleMapNum, null);
                        }
                        //判断是否带标题前缀，以及获取前缀及前缀层级
                        int titleLevel = docxCommonService.getCurrentValidTitleLevel(textCurrentTitleMapNum);
                        String preNum = docxCommonService.extractPrefixByLevel(pgText, titleLevel);
                        //计算段落层级信息
                        StringBuilder prefixPath = new StringBuilder();
                        for (int i=1; i<=MAX_TITLE_LEVEL; i++) {
                            String s = textCurrentTitleMapNum.get(i);
                            if (!StringUtils.isEmpty(s)) {
                                prefixPath.append(s).append(";");
                            }
                        }
                        //落地解析明细表
                        ContractAnalysisDetail detailObj = new ContractAnalysisDetail();
                        detailObj.setMasterId(mainObjId);
                        detailObj.setModule(textCurrentModuleId);
                        if (titleLevel>0 && !StringUtils.isEmpty(preNum)) {
                            detailObj.setIsBelongTitle(CONSTANTS_YES);
                            detailObj.setTitleLevel(String.valueOf(titleLevel));
                            detailObj.setTitlePrefix(preNum);
                        } else {
                            detailObj.setIsBelongTitle(CONSTANTS_NO);
                            detailObj.setTitleLevel("");
                            detailObj.setTitlePrefix("");
                        }
                        detailObj.setFirstTitle(textCurrentTitleMapNum.get(1));
                        detailObj.setSecondTitle(textCurrentTitleMapNum.get(2));
                        detailObj.setThirdTitle(textCurrentTitleMapNum.get(3));
                        detailObj.setFourthTitle(textCurrentTitleMapNum.get(4));
                        detailObj.setFifthTitle(textCurrentTitleMapNum.get(5));
                        detailObj.setOriginalContent(pgText);
                        detailObj.setParaLevelInfo(prefixPath.toString());
                        detailList.add(detailObj);
                    }
                }
            }
        }

        if (detailList.size() > 0) {
            log.info("处理文件落地信息完成，开始落地处理....");
            int batchSize = 500;
            for (int i = 0; i < detailList.size(); i += batchSize) {
                List<ContractAnalysisDetail> detailSubList = new ArrayList<>();
                for (int j = i; j < Math.min(detailList.size(), i + batchSize); j++) {
                    detailSubList.add(detailList.get(j));
                }
                try{
                    // 方法一：自定义批量插入
                    contractAnalysisDetailMapper.insertBatch2(detailSubList);
                    /* 方法二：MP自带的批量插入。无法使用（是假批量，效率不高）
                    boolean insertSuccess = detailService.saveBatch(detailSubList);
                    if (!insertSuccess) {
                        log.error("文档解析详情表落表失败");
                        throw new RuntimeException();
                    }*/
                    /* 方法三：多线程插入。无法使用（容易不同线程生成的主键ID冲突）
                    multiThreadInsert(detailSubList);
                    */
                }catch (Exception e){
                    log.error("文档解析详情表落表失败，起始段落：{}；终止段落：{}", detailSubList.get(0), detailSubList.get(detailSubList.size()-1));
                    throw e;
                }
            }
            log.info("文件落地完成...");
        }
        // 返回主对象ID
        return mainObjId;
    }


    /**
     * 多线程批量插入，但是多线程插入时可能不同线程生成相同的ID主键，导致主键冲突报错。因此暂不可用
     */
    public void multiThreadInsert(List<ContractAnalysisDetail> totalList) {
        if (totalList == null || totalList.isEmpty()) {
            return;
        }
        // 每批次大小（Oracle/OceanBase 推荐 500~1000）
        int batchSize = 500;
        int totalSize = totalList.size();
        // 异步任务集合
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        // 数据分片 + 提交多线程任务
        log.info("处理文件落地信息完成，开始多线程落地处理....");
        for (int i = 0; i < totalSize; i += batchSize) {
            List<ContractAnalysisDetail> subList = new ArrayList<>();
            for (int j = i; j < Math.min(totalSize, i + batchSize); j++) {
                subList.add(totalList.get(j));
            }
            // 异步执行
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                log.info("当前执行线程：{}，批次数据量：{}",
                        Thread.currentThread().getName(),
                        subList.size());
                try {
                    doBatchInsert(subList);
                } catch (Throwable e) { // 捕获所有异常
                    log.error("落库失败，批次数据量:{}", subList.size(), e);
                    // 必须抛出，让主线程感知失败
                    throw new RuntimeException("子线程执行失败：" + e.getMessage());
                }
            }, taskExecutor);
            futureList.add(future);
        }

        try {
            // 等待所有线程完成
            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();
            log.info("多线程落地处理文件落地信息完成！");
        } catch (Throwable e) {
            log.error("批量插入总体失败", e);
            // 这里抛出异常，让外层知道失败，不会卡死
            throw new RuntimeException("批量插入失败：" + e.getMessage());
        }
    }

    /**
     * 单批次插入
     */
    public void doBatchInsert(List<ContractAnalysisDetail> list) {
        try{
            boolean insertSuccess = detailService.saveBatch(list);
            if (!insertSuccess) {
                log.error("文档解析详情表落表失败");
                throw new RuntimeException();
            }
        }catch (Exception e){
            log.error("文档解析详情表落表失败");
            throw e;
        }
    }

    /**
     * 初始化文本标题层级Map
     */
    private Map<Integer, String> initTextTitleMap() {
        Map<Integer, String> titleMap = new HashMap<>();
        for (int i = 1; i <= MAX_TITLE_LEVEL; i++) {
            titleMap.put(i, "");
        }
        return titleMap;
    }
}

