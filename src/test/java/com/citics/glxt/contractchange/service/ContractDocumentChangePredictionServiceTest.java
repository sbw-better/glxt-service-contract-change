package com.citics.glxt.contractchange.service;

import com.citics.glxt.common.constants.CommonConstants;
import com.citics.glxt.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.DocumentChangeTypeSummaryResponse;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContractDocumentChangePredictionServiceTest {
    @Test
    public void shouldPredictBeforeCallingLegacyLandingService() {
        ContractDocumentParagraphReader reader = mock(ContractDocumentParagraphReader.class);
        ContractParagraphPredictionService predictionService = mock(ContractParagraphPredictionService.class);
        LegacyDocxAnalysisTransactionService landingService = mock(LegacyDocxAnalysisTransactionService.class);
        DocxAnalysisDTO request = request();
        when(reader.readPredictionParagraphs(request)).thenReturn(Arrays.asList(" 段落一 ", "段落一", "段落二"));
        when(predictionService.predictBatch(eq(Arrays.asList("段落一", "段落二")), eq("employee-001")))
                .thenReturn(Arrays.asList(high("28"), candidate("19")));
        when(landingService.analyzeAndLand(request)).thenReturn(88L);

        ContractDocumentChangePredictionService service = new ContractDocumentChangePredictionService(reader,
                predictionService, new FileChangeTypeAggregator(), landingService);
        DocumentChangeTypeSummaryResponse response = service.analyzeAndPredict(request, "employee-001");

        assertEquals(Long.valueOf(88L), response.getMainId());
        assertEquals(Collections.singletonList("28"), response.getFileChangeTypeCodes());
        verify(predictionService).predictBatch(eq(Arrays.asList("段落一", "段落二")), eq("employee-001"));
        verify(landingService).analyzeAndLand(request);
    }

    @Test
    public void shouldNotLandWhenPredictionFails() {
        ContractDocumentParagraphReader reader = mock(ContractDocumentParagraphReader.class);
        ContractParagraphPredictionService predictionService = mock(ContractParagraphPredictionService.class);
        LegacyDocxAnalysisTransactionService landingService = mock(LegacyDocxAnalysisTransactionService.class);
        DocxAnalysisDTO request = request();
        when(reader.readPredictionParagraphs(request)).thenReturn(Collections.singletonList("段落一"));
        when(predictionService.predictBatch(anyList(), eq("employee-001")))
                .thenThrow(new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE, "模型不可用"));
        ContractDocumentChangePredictionService service = new ContractDocumentChangePredictionService(reader,
                predictionService, new FileChangeTypeAggregator(), landingService);

        try {
            service.analyzeAndPredict(request, "employee-001");
            fail("应透传模型不可用异常");
        } catch (ContractChangeBusinessException ex) {
            assertEquals(CommonConstants.SERVICE_UNAVAILABLE, ex.getCode());
        }
        verify(landingService, never()).analyzeAndLand(any(DocxAnalysisDTO.class));
    }

    private DocxAnalysisDTO request() {
        DocxAnalysisDTO request = new DocxAnalysisDTO();
        request.setFileGetPath("contract.docx");
        return request;
    }

    private PredictionResponse high(String code) {
        return prediction(new ChangeTypePrediction(code, 1D, 1, "HIGH"));
    }

    private PredictionResponse candidate(String code) {
        return prediction(new ChangeTypePrediction(code, 0.6D, 1, "CANDIDATE"));
    }

    private PredictionResponse prediction(ChangeTypePrediction type) {
        return new PredictionResponse("SEMANTIC", "test-v1", 0.9D,
                Collections.singletonList(type), Collections.emptyList());
    }
}
