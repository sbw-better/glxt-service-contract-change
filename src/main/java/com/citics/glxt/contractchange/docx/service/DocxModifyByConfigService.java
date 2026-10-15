package com.citics.glxt.contractchange.docx.service;

import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import org.docx4j.openpackaging.exceptions.Docx4JException;

public interface DocxModifyByConfigService {
    /**
     * 解析文件，修改xml文件，生成新文件
     * @param docxAnalysisDTO 入参
     * @return
     * @throws Docx4JException
     */
    String analyzeAndGenerate(DocxAnalysisDTO docxAnalysisDTO) throws Docx4JException;
}
