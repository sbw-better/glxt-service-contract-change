package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.service.impl.DocxCommonServiceImpl;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    public void shouldNotReadNumberingForParagraphWithoutNumPr() throws Exception {
        DocxCommonService commonService = mock(DocxCommonService.class);
        WordprocessingMLPackage document = mock(WordprocessingMLPackage.class);
        MainDocumentPart mainPart = mock(MainDocumentPart.class);
        NumberingDefinitionsPart numberingPart = new NumberingDefinitionsPart();
        numberingPart.setJaxbElement(new Numbering());
        P paragraph = new P();
        paragraph.setPPr(new PPr());
        DocxAnalysisDTO request = new DocxAnalysisDTO();
        request.setFileGetPath("contract.docx");

        when(commonService.getXmlDocument("contract.docx")).thenReturn(document);
        when(document.getMainDocumentPart()).thenReturn(mainPart);
        when(mainPart.getContent()).thenReturn(java.util.Collections.<Object>singletonList(paragraph));
        when(mainPart.getNumberingDefinitionsPart()).thenReturn(numberingPart);
        when(commonService.extractFullParagraphTextList(paragraph.getContent()))
                .thenReturn(java.util.Collections.singletonList("特别约定"));
        when(commonService.getCurrentModule("特别约定", 0L)).thenReturn(2L);

        new ContractDocumentParagraphReader(commonService).readPredictionParagraphs(request);

        verify(commonService, never()).getTitleModeFromP(org.mockito.ArgumentMatchers.any(Numbering.class),
                org.mockito.ArgumentMatchers.any(PPr.class));
    }
}
