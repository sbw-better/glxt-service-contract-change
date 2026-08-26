package com.citics.glxt.contractchange.controller;

import com.citics.glxt.contractchange.common.GlobalExceptionHandler;
import com.citics.glxt.contractchange.model.ImportErrorItem;
import com.citics.glxt.contractchange.model.ImportResponse;
import com.citics.glxt.contractchange.service.ContractParagraphImportService;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService;
import com.citics.glxt.contractchange.service.ParagraphVectorIndexService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证业务接口必须接收并透传模型平台审计所需的 UserId。 */
public class ContractChangeControllerTest {
    private ContractParagraphImportService importService;
    private ContractParagraphPredictionService predictionService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        importService = mock(ContractParagraphImportService.class);
        predictionService = mock(ContractParagraphPredictionService.class);
        ParagraphVectorIndexService indexService = mock(ParagraphVectorIndexService.class);
        ContractChangeController controller = new ContractChangeController(
                importService, predictionService, indexService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    public void shouldRejectPredictRequestWithoutUserId() throws Exception {
        mockMvc.perform(post("/service/contract-change/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paragraph\":\"测试合同段落\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("UserId请求头不能为空"));
    }

    @Test
    public void shouldPassUserIdToPredictionService() throws Exception {
        mockMvc.perform(post("/service/contract-change/predict")
                .header("UserId", "employee-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paragraph\":\"测试合同段落\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(predictionService).predict("测试合同段落", "employee-001");
    }

    @Test
    public void shouldReturnBadRequestCodeForExcelValidationFailure() throws Exception {
        ImportResponse failure = new ImportResponse(false, 1, 0, 0, 0, false,
                Collections.singletonList(new ImportErrorItem(2, "合同段落不能为空")));
        when(importService.importExcel(any(), eq("employee-001"))).thenReturn(failure);
        MockMultipartFile file = new MockMultipartFile("file", "samples.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        mockMvc.perform(multipart("/service/contract-change/samples/import")
                .file(file).header("UserId", "employee-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.errors[0].row").value(2));
    }

    @Test
    public void shouldRejectMissingExcelFileAsBadRequest() throws Exception {
        mockMvc.perform(multipart("/service/contract-change/samples/import")
                .header("UserId", "employee-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("file文件不能为空"));
    }

    @Test
    public void shouldRejectMalformedJsonAsBadRequest() throws Exception {
        mockMvc.perform(post("/service/contract-change/predict")
                .header("UserId", "employee-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid-json}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求JSON格式不正确"));
    }
}
