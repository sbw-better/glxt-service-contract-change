package com.citics.glxt.contractchange.contractcompare.controller;

import com.citics.glxt.contractchange.common.handler.GlobalExceptionHandler;
import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogStatus;
import com.citics.glxt.contractchange.contractcompare.model.ContractChangeAnalysisResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.service.ContractChangeAnalysisService;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ContractCompareControllerTest {
    private ContractCompareService compareService;
    private ContractChangeAnalysisService analysisService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        compareService = mock(ContractCompareService.class);
        analysisService = mock(ContractChangeAnalysisService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ContractCompareController(compareService, analysisService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    public void shouldReturnSlimCompareResponse() throws Exception {
        when(analysisService.analyze(any(ContractCompareRequest.class))).thenReturn(
                new ContractChangeAnalysisResponse("analysis-1", Arrays.asList("04", "19"),
                        AnalysisLogStatus.SUCCESS));

        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instId\":10001,\"userId\":\"employee-001\"," +
                        "\"analysisType\":\"DOUBLE_VERSION\"," +
                        "\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.data.fileChangeTypeCodes[0]").value("04"))
                .andExpect(jsonPath("$.data.fileChangeTypeCodes[1]").value("19"))
                .andExpect(jsonPath("$.data.analysisStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalChanges").doesNotExist())
                .andExpect(jsonPath("$.data.changes").doesNotExist());

        verify(analysisService).analyze(any(ContractCompareRequest.class));
        verifyZeroInteractions(compareService);
    }

    @Test
    public void shouldRequireUserIdInBody() throws Exception {
        when(analysisService.analyze(any(ContractCompareRequest.class)))
                .thenThrow(new ContractChangeBusinessException("userId不能为空"));
        mockMvc.perform(post("/service/contract-compare/compare")
                .header("UserId", "header-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instId\":10001,\"analysisType\":\"DOUBLE_VERSION\"," +
                        "\"oldFileGetPath\":\"/old.docx\"," +
                        "\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(analysisService).analyze(any(ContractCompareRequest.class));
        verifyZeroInteractions(compareService);
    }

    @Test
    public void shouldRequireInstIdInBody() throws Exception {
        when(analysisService.analyze(any(ContractCompareRequest.class)))
                .thenThrow(new ContractChangeBusinessException("instId不能为空"));
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"employee-001\",\"analysisType\":\"DOUBLE_VERSION\"," +
                        "\"oldFileGetPath\":\"/old.docx\"," +
                        "\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(analysisService).analyze(any(ContractCompareRequest.class));
        verifyZeroInteractions(compareService);
    }

    @Test
    public void shouldKeepExportIndependentFromCompareMetadataAndPrediction() throws Exception {
        byte[] excel = new byte[]{1, 2, 3};
        when(compareService.exportExcel(any(ContractCompareRequest.class))).thenReturn(excel);

        mockMvc.perform(post("/service/contract-compare/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldFileGetPath\":\"/old.docx\",\"newFileGetPath\":\"/new.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=contract-compare-result.xlsx"))
                .andExpect(content().bytes(excel));

        verify(compareService).exportExcel(any(ContractCompareRequest.class));
        verifyZeroInteractions(analysisService);
    }

    @Test
    public void shouldRequireAnalysisTypeForCompare() throws Exception {
        when(analysisService.analyze(any(ContractCompareRequest.class)))
                .thenThrow(new ContractChangeBusinessException("analysisType不能为空"));
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instId\":10001,\"userId\":\"employee-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(analysisService).analyze(any(ContractCompareRequest.class));
        verifyZeroInteractions(compareService);
    }

    @Test
    public void shouldRejectUnknownAnalysisTypeBeforeCallingServices() throws Exception {
        mockMvc.perform(post("/service/contract-compare/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instId\":10001,\"userId\":\"employee-001\"," +
                        "\"analysisType\":\"LETTER\",\"changeFileGetPath\":\"/a.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verifyZeroInteractions(analysisService, compareService);
    }
}
