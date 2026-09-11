package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在独立短事务中调用已上线的文档解析落库服务。 */
@Service
public class LegacyDocxAnalysisTransactionService {
    private final DocxModifyAndLandService docxModifyAndLandService;

    public LegacyDocxAnalysisTransactionService(DocxModifyAndLandService docxModifyAndLandService) {
        this.docxModifyAndLandService = docxModifyAndLandService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long analyzeAndLand(DocxAnalysisDTO request) {
        return docxModifyAndLandService.analyzeAndLand(request);
    }
}
