package com.citics.glxt.contractchange.docx.aspose.service;

import com.aspose.words.Document;
import com.citics.glxt.contractchange.docx.aspose.model.ContractParagraph;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisMain;
import com.citics.glxt.contractchange.docx.mapper.ContractAnalysisDetailMapper;
import com.citics.glxt.contractchange.docx.mapper.ContractAnalysisMainMapper;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.citics.glxt.contractchange.constants.Constants.OPERATION_GUIDANCE_GENERATE_CONDITION_THREE;
import static com.citics.glxt.contractchange.constants.Constants.MAX_TITLE_LEVEL;

/** 使用 Aspose 解析合同段落并写入数据库。 */
@Slf4j
@Service
public class AsposeAnalyzeAndLandService {
    private static final int BATCH_SIZE = 500;

    @Resource private AsposeDocumentIO documentIO;
    @Resource private AsposeContractParser parser;
    @Resource private ContractAnalysisMainMapper mainMapper;
    @Resource private ContractAnalysisDetailMapper detailMapper;

    public Long analyzeAndLand(DocxAnalysisDTO request) {
        if (request == null || !StringUtils.hasText(request.getFileGetPath())) {
            throw new IllegalArgumentException("文档获取地址不得为空，请核对！");
        }
        Document document = documentIO.load(request.getFileGetPath());
        List<ContractParagraph> paragraphs;
        try {
            paragraphs = parser.parse(document);
        } catch (Exception e) {
            throw new IllegalStateException("合同章节解析失败", e);
        }
        if (document.getSections().getCount() == 0) {
            throw new IllegalArgumentException("文档解析时或因格式问题读取为空，请手工处理！");
        }

        ContractAnalysisMain main = new ContractAnalysisMain();
        main.setType(OPERATION_GUIDANCE_GENERATE_CONDITION_THREE);
        main.setFileGetPath(request.getFileGetPath());
        main.setAnalysisTime(new Date());
        if (mainMapper.insert(main) <= 0 || main.getId() == null) {
            throw new IllegalStateException("文档解析主表落表失败");
        }

        List<ContractAnalysisDetail> details = new ArrayList<ContractAnalysisDetail>();
        for (ContractParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (!StringUtils.hasText(text)) continue;
            ContractAnalysisDetail detail = new ContractAnalysisDetail();
            detail.setMasterId(main.getId());
            detail.setModule(paragraph.getModule());
            detail.setIsBelongTitle(paragraph.getTitleLevel() > 0 ? 1 : 0);
            detail.setTitleLevel(paragraph.getTitleLevel() > 0
                    ? String.valueOf(paragraph.getTitleLevel()) : "");
            detail.setTitlePrefix(paragraph.getTitleLevel() > 0 ? paragraph.getTitlePrefix() : "");
            String[] titleNumbers = paragraph.getTitleNumbers();
            StringBuilder levelPath = new StringBuilder();
            for (int i = 0; i < MAX_TITLE_LEVEL; i++) {
                if (StringUtils.hasText(titleNumbers[i])) {
                    setTitle(detail, i, titleNumbers[i]);
                    levelPath.append(titleNumbers[i]).append(';');
                }
            }
            detail.setParaLevelInfo(levelPath.toString());
            detail.setOriginalContent(text);
            details.add(detail);
        }

        for (int start = 0; start < details.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, details.size());
            List<ContractAnalysisDetail> batch = details.subList(start, end);
            detailMapper.insertBatch2(batch);
            log.info("Aspose 合同解析明细落库完成，主表ID：{}，批次：{}-{}，条数：{}",
                    main.getId(), start + 1, end, batch.size());
        }
        log.info("Aspose 合同解析落地完成，主表ID：{}，段落数：{}",
                main.getId(), details.size());
        return main.getId();
    }

    private void setTitle(ContractAnalysisDetail detail, int index, String value) {
        switch (index) {
            case 0: detail.setFirstTitle(value); break;
            case 1: detail.setSecondTitle(value); break;
            case 2: detail.setThirdTitle(value); break;
            case 3: detail.setFourthTitle(value); break;
            case 4: detail.setFifthTitle(value); break;
            default: break;
        }
    }
}







