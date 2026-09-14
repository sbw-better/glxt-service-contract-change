package com.citics.glxt.contractchange.contractcompare.controller;

import com.citics.glxt.common.handler.GlobalExceptionHandler;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
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
import static org.mockito.Mockito.when;
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
}
