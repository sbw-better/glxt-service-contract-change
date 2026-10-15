package com.citics.glxt.contractchange.docx.service;

import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;

public interface DocxGenerateByRevision {
    /**
     * 根据修订记录生成补充协议
     * @param docxAnalysisDTO 入参
     * @return
     */
    String generateByRevision(DocxAnalysisDTO docxAnalysisDTO);
}
