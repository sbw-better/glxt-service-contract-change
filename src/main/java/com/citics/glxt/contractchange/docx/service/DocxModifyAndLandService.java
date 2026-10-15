package com.citics.glxt.contractchange.docx.service;

import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;

public interface DocxModifyAndLandService {
    /**
     * 解析文档，并落地段落，返回主对象ID，即TPIF_HTJXZB的ID
     * @param docxAnalysisDTO
     * @return
     */
    Long analyzeAndLand(DocxAnalysisDTO docxAnalysisDTO);
}
