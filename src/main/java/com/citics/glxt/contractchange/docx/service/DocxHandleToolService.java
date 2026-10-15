package com.citics.glxt.contractchange.docx.service;

import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;

public interface DocxHandleToolService {
    /**
     * 经办人关键字处理
     * @param docxAnalysisDTO 入参
     * @return
     */
    String jbrKeywordsHandle(DocxAnalysisDTO docxAnalysisDTO);
}
