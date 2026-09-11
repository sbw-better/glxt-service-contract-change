package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.service.impl.DocxCommonServiceImpl;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class ContractDocumentParagraphReaderTest {
    @Test
    public void shouldReadOnlyEligibleNonTitleParagraphs() throws Exception {
        DocxCommonServiceImpl commonService = spy(new DocxCommonServiceImpl());
        WordprocessingMLPackage document = WordprocessingMLPackage.createPackage();
        for (String paragraph : Arrays.asList("合同封面", "封面内容", "特别约定", "特别约定正文",
                "风险揭示书", "风险正文", "目录", "目录内容", "一、合同当事人", "正文内容", "签署页", "签署内容")) {
            document.getMainDocumentPart().addParagraphOfText(paragraph);
        }
        doReturn(document).when(commonService).getXmlDocument("contract.docx");
        DocxAnalysisDTO request = new DocxAnalysisDTO();
        request.setFileGetPath("contract.docx");

        assertEquals(Arrays.asList("特别约定正文", "风险正文", "正文内容"),
                new ContractDocumentParagraphReader(commonService).readPredictionParagraphs(request));
    }
}
