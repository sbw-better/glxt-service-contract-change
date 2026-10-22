package com.citics.glxt.contractchange.contractquery.controller;

import com.citics.glxt.contractchange.common.handler.GlobalExceptionHandler;
import com.citics.glxt.contractchange.contractquery.model.ContractInfo;
import com.citics.glxt.contractchange.contractquery.model.ContractFileDownload;
import com.citics.glxt.contractchange.contractquery.model.ContractQueryRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadPayload;
import com.citics.glxt.contractchange.contractquery.service.ContractQueryService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

public class ContractQueryControllerTest {
    private ContractQueryService queryService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        queryService = mock(ContractQueryService.class);
        when(queryService.queryContracts(org.mockito.ArgumentMatchers.any(ContractQueryRequest.class)))
                .thenReturn(Collections.<ContractInfo>emptyList());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractQueryController(queryService))
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }

    @Test
    public void shouldDefaultQueryDaysToZero() throws Exception {
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"managerId\":525,\"baseDate\":\"2026-10-09\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray()).andExpect(jsonPath("$.data.length()").value(0));
        org.mockito.ArgumentCaptor<ContractQueryRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ContractQueryRequest.class);
        verify(queryService).queryContracts(captor.capture());
        org.junit.Assert.assertEquals(Long.valueOf(525L), captor.getValue().getManagerId());
        org.junit.Assert.assertEquals(LocalDate.parse("2026-10-09"), captor.getValue().getBaseDate());
        org.junit.Assert.assertEquals(Integer.valueOf(0), captor.getValue().getQueryDays());
    }

    @Test
    public void shouldAcceptQueryDayBoundariesZeroAndSix() throws Exception {
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"managerId\":525,\"baseDate\":\"2026-10-09\",\"queryDays\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"managerId\":525,\"baseDate\":\"2026-10-09\",\"queryDays\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        org.mockito.ArgumentCaptor<ContractQueryRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ContractQueryRequest.class);
        org.mockito.Mockito.verify(queryService, org.mockito.Mockito.times(2)).queryContracts(captor.capture());
        org.junit.Assert.assertEquals(Integer.valueOf(0), captor.getAllValues().get(0).getQueryDays());
        org.junit.Assert.assertEquals(Integer.valueOf(6), captor.getAllValues().get(1).getQueryDays());
    }

    @Test
    public void shouldRejectQueryDaysOutsideAllowedRange() throws Exception {
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"managerId\":525,\"baseDate\":\"2026-10-09\",\"queryDays\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"managerId\":525,\"baseDate\":\"2026-10-09\",\"queryDays\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    public void shouldRejectMissingOrMalformedRequiredBodyFields() throws Exception {
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"baseDate\":\"2026-10-09\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/service/contract-query/doc/query").contentType("application/json")
                .content("{\"managerId\":525,\"baseDate\":\"2026/10/09\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    public void shouldReturnSingleFileStreamAfterOwnerValidation() throws Exception {
        when(queryService.downloadContracts(org.mockito.ArgumentMatchers.any(ContractDownloadRequest.class)))
                .thenReturn(new ContractDownloadPayload("基金合同_20261021120000000.docx", "Contract_20261021120000000.docx",
                        "application/octet-stream", new byte[]{1, 2, 3}));

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/service/contract-query/doc/download").contentType("application/json")
                        .content("{\"managerId\":525,\"fileIds\":[123]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().bytes(new byte[]{1, 2, 3})).andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        org.junit.Assert.assertTrue(disposition.contains("filename*=UTF-8''%E5%9F%BA%E9%87%91%E5%90%88%E5%90%8C_20261021120000000.docx"));
        org.junit.Assert.assertTrue(disposition.contains("filename=\"Contract_"));

        verify(queryService).downloadContracts(org.mockito.ArgumentMatchers.any(ContractDownloadRequest.class));
    }

    @Test
    public void shouldZipMultipleFiles() throws Exception {
        when(queryService.downloadContracts(org.mockito.ArgumentMatchers.any(ContractDownloadRequest.class)))
                .thenReturn(new ContractDownloadPayload("基金合同_20261021120000000.zip", "Contracts_20261021120000000.zip",
                        "application/zip", new byte[]{1, 2}));

        mockMvc.perform(post("/service/contract-query/doc/download").contentType("application/json")
                        .content("{\"managerId\":525,\"fileIds\":[123,456]}"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''%E5%9F%BA%E9%87%91%E5%90%88%E5%90%8C_20261021120000000.zip")));
    }

    @Test
    public void shouldNotFindFilesWhenOwnershipValidationFails() throws Exception {
        when(queryService.downloadContracts(org.mockito.ArgumentMatchers.any(ContractDownloadRequest.class)))
                .thenReturn(null);
        mockMvc.perform(post("/service/contract-query/doc/download").contentType("application/json")
                        .content("{\"managerId\":526,\"fileIds\":[123]}"))
                .andExpect(status().isNotFound());
    }
}
