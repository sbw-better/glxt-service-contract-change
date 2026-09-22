package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogDO;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogStatus;
import com.citics.glxt.contractchange.contractcompare.model.ContractChangeAnalysisResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseContext;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.PredictionSummary;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractor;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractorRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContractChangeAnalysisServiceTest {
    private ContractChangeExtractorRegistry registry;
    private ContractChangeExtractor extractor;
    private ContractComparePredictionService predictionService;
    private AnalysisResultPersistenceService persistenceService;
    private ContractChangeAnalysisService service;

    @Before
    public void setUp() {
        registry = mock(ContractChangeExtractorRegistry.class);
        extractor = mock(ContractChangeExtractor.class);
        predictionService = mock(ContractComparePredictionService.class);
        persistenceService = mock(AnalysisResultPersistenceService.class);
        when(registry.get(AnalysisType.DOUBLE_VERSION)).thenReturn(extractor);
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setModelName("embedding-model");
        properties.getEmbedding().setModelVersion("model-v1");
        service = new ContractChangeAnalysisService(registry, predictionService,
                persistenceService, properties, new ObjectMapper());
    }

    @Test
    public void shouldPersistFullSnapshotBeforeReturningSlimSuccess() {
        ContractCompareResponse detail = detailWithContext();
        when(extractor.extract(any(ContractCompareRequest.class))).thenReturn(detail);
        doAnswer(invocation -> {
            ContractCompareResponse response = invocation.getArgument(0);
            response.setPredictionSummary(new PredictionSummary(1, 0, 0, 0));
            response.setFileChangeTypeCodes(Arrays.asList("04", "19"));
            return null;
        }).when(predictionService).predict(eq(detail), eq(AnalysisType.DOUBLE_VERSION),
                eq("employee-001"));

        ContractCompareRequest request = request();
        request.setUserId(" employee-001 ");
        ContractChangeAnalysisResponse result = service.analyze(request);

        assertEquals(Arrays.asList("04", "19"), result.getFileChangeTypeCodes());
        assertEquals(AnalysisLogStatus.SUCCESS, result.getAnalysisStatus());
        ArgumentCaptor<AnalysisLogDO> processing = ArgumentCaptor.forClass(AnalysisLogDO.class);
        verify(persistenceService).createProcessing(processing.capture());
        assertTrue(result.getAnalysisId().matches("[0-9a-f]{32}"));
        assertEquals(processing.getValue().getAnalysisId(), result.getAnalysisId());
        assertEquals(Long.valueOf(10001L), processing.getValue().getInstId());
        assertEquals("employee-001", processing.getValue().getUserId());
        assertEquals("PROCESSING", processing.getValue().getStatus());

        ArgumentCaptor<AnalysisLogDO> completed = ArgumentCaptor.forClass(AnalysisLogDO.class);
        verify(persistenceService).persistSuccess(completed.capture(), eq(detail),
                eq(AnalysisType.DOUBLE_VERSION));
        assertEquals(result.getAnalysisId(), completed.getValue().getAnalysisId());
        assertEquals("SUCCESS", completed.getValue().getStatus());
        assertTrue(completed.getValue().getResultJson().contains("\"changes\""));
        assertTrue(completed.getValue().getResultJson().contains("完整旧条款"));
        assertTrue(completed.getValue().getResultJson().contains("\"fileChangeTypeCodes\":[\"04\",\"19\"]"));
    }

    @Test
    public void shouldReturnPartialSuccessWhenAnyPredictionFailedOrWasSkipped() {
        ContractCompareResponse detail = detailWithContext();
        detail.setPredictionSummary(new PredictionSummary(1, 0, 1, 0));
        detail.setFileChangeTypeCodes(Collections.singletonList("04"));
        when(extractor.extract(any(ContractCompareRequest.class))).thenReturn(detail);

        ContractChangeAnalysisResponse result = service.analyze(request());

        assertEquals(AnalysisLogStatus.PARTIAL_SUCCESS, result.getAnalysisStatus());
        ArgumentCaptor<AnalysisLogDO> completed = ArgumentCaptor.forClass(AnalysisLogDO.class);
        verify(persistenceService).persistSuccess(completed.capture(), eq(detail),
                eq(AnalysisType.DOUBLE_VERSION));
        assertEquals("PARTIAL_SUCCESS", completed.getValue().getStatus());
    }

    @Test
    public void shouldReturnSuccessAndEmptyCodesForNormalNoMatch() {
        ContractCompareResponse detail = new ContractCompareResponse(0,
                Collections.<ClauseChange>emptyList(), Collections.<String>emptyList());
        detail.setPredictionSummary(new PredictionSummary(0, 2, 0, 0));
        when(extractor.extract(any(ContractCompareRequest.class))).thenReturn(detail);

        ContractChangeAnalysisResponse result = service.analyze(request());

        assertEquals(AnalysisLogStatus.SUCCESS, result.getAnalysisStatus());
        assertTrue(result.getFileChangeTypeCodes().isEmpty());
    }

    @Test
    public void shouldStopBeforeAnalysisWhenProcessingRecordCannotBeCreated() {
        doThrow(new IllegalStateException("db unavailable"))
                .when(persistenceService).createProcessing(any(AnalysisLogDO.class));

        try {
            service.analyze(request());
        } catch (IllegalStateException expected) {
            assertEquals("db unavailable", expected.getMessage());
        }

        verify(extractor).validateRequest(any(ContractCompareRequest.class));
        verify(extractor, never()).extract(any(ContractCompareRequest.class));
        verify(predictionService, never()).predict(any(ContractCompareResponse.class),
                any(AnalysisType.class), anyString());
        verify(persistenceService, never()).markFailed(anyString(), anyLong(), anyString());
    }

    @Test
    public void shouldRequireExplicitAnalysisTypeBeforeCreatingRecord() {
        ContractCompareRequest request = request();
        request.setAnalysisType(null);

        try {
            service.analyze(request);
            fail("analysisType should be required");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("analysisType"));
        }

        verify(persistenceService, never()).createProcessing(any(AnalysisLogDO.class));
    }

    @Test
    public void shouldRequireUserIdBeforeCreatingRecord() {
        ContractCompareRequest request = request();
        request.setUserId(" ");

        try {
            service.analyze(request);
            fail("userId should be required");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("userId"));
        }

        verify(persistenceService, never()).createProcessing(any(AnalysisLogDO.class));
    }

    @Test
    public void shouldRequireInstIdBeforeCreatingRecord() {
        ContractCompareRequest request = request();
        request.setInstId(null);

        try {
            service.analyze(request);
            fail("instId should be required");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("instId"));
        }

        verify(persistenceService, never()).createProcessing(any(AnalysisLogDO.class));
    }

    @Test
    public void shouldValidateStrategyRequestBeforeCreatingRecord() {
        doThrow(new RuntimeException("修改前合同文件路径不能为空"))
                .when(extractor).validateRequest(any(ContractCompareRequest.class));

        try {
            service.analyze(request());
            fail("strategy request should be validated");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("修改前合同文件路径"));
        }

        verify(persistenceService, never()).createProcessing(any(AnalysisLogDO.class));
    }

    @Test
    public void shouldMarkCreatedRecordFailedWhenFinalPersistenceFails() {
        ContractCompareResponse detail = detailWithContext();
        when(extractor.extract(any(ContractCompareRequest.class))).thenReturn(detail);
        doThrow(new IllegalStateException("detail insert failed"))
                .when(persistenceService).persistSuccess(any(AnalysisLogDO.class), eq(detail),
                        eq(AnalysisType.DOUBLE_VERSION));

        try {
            service.analyze(request());
        } catch (IllegalStateException expected) {
            assertEquals("detail insert failed", expected.getMessage());
        }

        ArgumentCaptor<AnalysisLogDO> processing = ArgumentCaptor.forClass(AnalysisLogDO.class);
        verify(persistenceService).createProcessing(processing.capture());
        verify(persistenceService).markFailed(eq(processing.getValue().getAnalysisId()), anyLong(),
                org.mockito.ArgumentMatchers.contains("detail insert failed"));
    }

    private ContractCompareRequest request() {
        ContractCompareRequest request = new ContractCompareRequest();
        request.setInstId(10001L);
        request.setUserId("employee-001");
        request.setAnalysisType(AnalysisType.DOUBLE_VERSION);
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");
        return request;
    }

    private ContractCompareResponse detailWithContext() {
        ClauseChange change = new ClauseChange();
        change.setClauseNo("第二条");
        change.setContext(new ClauseContext("完整旧条款", "完整新条款"));
        change.setChangedParagraphs(Collections.emptyList());
        return new ContractCompareResponse(1, Collections.singletonList(change),
                Collections.<String>emptyList());
    }
}
