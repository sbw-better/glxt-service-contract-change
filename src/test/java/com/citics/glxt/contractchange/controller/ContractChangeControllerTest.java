package com.citics.glxt.contractchange.controller;

import com.citics.glxt.contractchange.common.GlobalExceptionHandler;
import com.citics.glxt.contractchange.service.ContractParagraphImportService;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService;
import com.citics.glxt.contractchange.service.ParagraphVectorIndexService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证业务接口必须接收并透传模型平台审计所需的 user_id。 */
public class ContractChangeControllerTest {
    private ContractParagraphPredictionService predictionService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        ContractParagraphImportService importService = mock(ContractParagraphImportService.class);
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
                .andExpect(jsonPath("$.message").value("user_id请求头不能为空"));
    }

    @Test
    public void shouldPassUserIdToPredictionService() throws Exception {
        mockMvc.perform(post("/service/contract-change/predict")
                .header("user_id", "employee-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paragraph\":\"测试合同段落\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(predictionService).predict("测试合同段落", "employee-001");
    }
}
