package com.citics.glxt.contractchange.contractcompare.controller;

import com.citics.glxt.common.handler.GlobalExceptionHandler;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeDetail;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseContext;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.DetailType;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ContractCompareControllerTest {
    private ContractCompareService service;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        service = mock(ContractCompareService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractCompareController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    public void shouldCompareTwoServerPathsWithoutUserId() throws Exception {
        when(service.compare(any())).thenReturn(new ContractCompareResponse(0,
                Collections.emptyList(), Collections.emptyList()));

        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalChanges").value(0));

        verify(service).compare(any(ContractCompareRequest.class));
    }

    @Test
    public void shouldRejectMissingNewPath() throws Exception {
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("修改后合同文件路径不能为空"));
    }

    @Test
    public void shouldTreatExplicitNullAnalysisTypeAsDoubleVersion() throws Exception {
        when(service.compare(any())).thenReturn(new ContractCompareResponse(0,
                Collections.emptyList(), Collections.emptyList()));

        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analysisType\":null,\"oldFileGetPath\":\"/old.docx\","
                        + "\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).compare(any(ContractCompareRequest.class));
    }

    @Test
    public void shouldAcceptChangeDocumentRequest() throws Exception {
        when(service.compare(any())).thenReturn(new ContractCompareResponse(0,
                Collections.emptyList(), Collections.emptyList()));

        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analysisType\":\"CHANGE_DOCUMENT\","
                        + "\"changeFileGetPath\":\"/supplement.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.changeDocumentType").doesNotExist());

        verify(service).compare(any(ContractCompareRequest.class));
    }

    @Test
    public void shouldRejectMissingChangeDocumentPath() throws Exception {
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analysisType\":\"CHANGE_DOCUMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("变更函文件路径不能为空"));

        verifyZeroInteractions(service);
    }

    @Test
    public void shouldExposeDetailedChangesWithoutContentBlocks() throws Exception {
        ChangeDetail detail = new ChangeDetail();
        detail.setDetailType(DetailType.REPLACED);
        detail.setOldText("100");
        detail.setNewText("120");
        ClauseChange change = new ClauseChange();
        ChangedParagraph paragraph = new ChangedParagraph();
        paragraph.setParagraphChangeType(ChangeType.MODIFIED);
        paragraph.setOldContent("金额100万元");
        paragraph.setNewContent("金额120万元");
        paragraph.setChangeDetails(Collections.singletonList(detail));
        change.setChangedParagraphs(Collections.singletonList(paragraph));
        when(service.compare(any())).thenReturn(new ContractCompareResponse(1,
                Collections.singletonList(change), Collections.emptyList()));

        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].changeDetails[0].detailType").value("REPLACED"))
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].changeDetails[0].oldText").value("100"))
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].paragraphChangeType").value("MODIFIED"))
                .andExpect(jsonPath("$.data.changes[0].clauseId").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].parentClauseId").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].level").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].oldClauseNo").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].newClauseNo").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].oldContent").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].newContent").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changeDetails").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].oldIndex").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].newIndex").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].context").doesNotExist())
                .andExpect(jsonPath("$.data.changeDocumentType").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].sourceHeading").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].targetClauseReference").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].contentType").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].oldParagraphIndex").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].newParagraphIndex").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].oldStart").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changedParagraphs[0].changeDetails[0].oldStart").doesNotExist());
    }

    @Test
    public void shouldDownloadComparisonExcel() throws Exception {
        byte[] excel = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(service.exportExcel(any())).thenReturn(excel);

        mockMvc.perform(post("/service/contract-compare/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=contract-compare-result.xlsx"))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(excel));

        verify(service).exportExcel(any(ContractCompareRequest.class));
    }

    @Test
    public void shouldDownloadChangeDocumentExcelWithDedicatedFilename() throws Exception {
        byte[] excel = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(service.exportExcel(any())).thenReturn(excel);

        mockMvc.perform(post("/service/contract-compare/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analysisType\":\"CHANGE_DOCUMENT\","
                        + "\"changeFileGetPath\":\"/supplement.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=contract-change-extract-result.xlsx"))
                .andExpect(content().bytes(excel));
    }

    @Test
    public void shouldExposeFullContextInContextMode() throws Exception {
        ClauseChange change = new ClauseChange();
        change.setClauseNo("第二条");
        change.setChangeType(ChangeType.MODIFIED);
        change.setChangedParagraphs(Collections.<ChangedParagraph>emptyList());
        change.setContext(new ClauseContext("修改前完整条款", "修改后完整条款"));
        when(service.compare(any())).thenReturn(new ContractCompareResponse(1,
                Collections.singletonList(change), Collections.emptyList()));

        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\","
                        + "\"resultMode\":\"CONTEXT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changes[0].context.oldContent").value("修改前完整条款"))
                .andExpect(jsonPath("$.data.changes[0].context.newContent").value("修改后完整条款"));
    }

    @Test
    public void shouldRejectUnknownResultModeBeforeComparison() throws Exception {
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\","
                        + "\"resultMode\":\"FULL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求JSON格式不正确"));

        verifyZeroInteractions(service);
    }

    @Test
    public void shouldRejectUnknownAnalysisTypeBeforeComparison() throws Exception {
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"analysisType\":\"LETTER\","
                        + "\"changeFileGetPath\":\"/supplement.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求JSON格式不正确"));

        verifyZeroInteractions(service);
    }
}
