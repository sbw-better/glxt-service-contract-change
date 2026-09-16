package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseContext;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService.LenientPrediction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContractComparePredictionServiceTest {
    private ContractParagraphPredictionService paragraphService;
    private ContractComparePredictionService service;

    @Before
    public void setUp() {
        paragraphService = mock(ContractParagraphPredictionService.class);
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setModelVersion("v1");
        properties.getSearch().setMaxParagraphLength(2000);
        service = new ContractComparePredictionService(paragraphService, properties);
    }

    @Test
    public void shouldFallbackToNewContextAndCapHighResultAsCandidate() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.success(noMatch())))
                .thenReturn(Collections.singletonList(LenientPrediction.success(highMatch())));
        ContractCompareResponse response = response(ChangeType.MODIFIED,
                "金额为100万元。", "金额为120万元。",
                new ClauseContext("第二条 金额 金额为100万元。其他旧约定。",
                        "第二条 金额 金额为120万元。其他新约定。"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        ChangedParagraph paragraph = paragraph(response);
        assertEquals("MATCHED", paragraph.getBusinessTypePrediction().getStatus());
        assertEquals("NEW_CONTEXT", paragraph.getBusinessTypePrediction().getInputScope());
        assertTrue(paragraph.getBusinessTypePrediction().isFallbackUsed());
        assertEquals("CANDIDATE",
                paragraph.getBusinessTypePrediction().getChangeTypes().get(0).getLevel());
        verify(paragraphService, times(2)).predictBatchLenient(anyList(), eq("u1"));
    }

    @Test
    public void shouldKeepParagraphCandidateWithoutContextFallback() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.success(candidateMatch())));
        ContractCompareResponse response = response(ChangeType.MODIFIED,
                "旧内容", "新内容", new ClauseContext("旧完整内容", "新完整内容"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        assertEquals("NEW_PARAGRAPH", paragraph(response).getBusinessTypePrediction().getInputScope());
        assertFalse(paragraph(response).getBusinessTypePrediction().isFallbackUsed());
        verify(paragraphService).predictBatchLenient(Collections.singletonList("新内容"), "u1");
    }

    @Test
    public void shouldUseOldParagraphForDeletion() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.success(candidateMatch())));
        ContractCompareResponse response = response(ChangeType.DELETED,
                "被删除的管理人职责。", null, new ClauseContext("删除前完整条款", null));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(paragraphService).predictBatchLenient(captor.capture(), eq("u1"));
        assertEquals(Collections.singletonList("被删除的管理人职责。"), captor.getValue());
        assertEquals("OLD_PARAGRAPH", paragraph(response).getBusinessTypePrediction().getInputScope());
    }

    @Test
    public void shouldDegradeFailedBatchAndKeepComparisonResult() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.failed("EMBEDDING_UNAVAILABLE")));
        ContractCompareResponse response = response(ChangeType.ADDED,
                null, "新增风险揭示。", new ClauseContext(null, "新增风险揭示。"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        assertEquals("FAILED", paragraph(response).getBusinessTypePrediction().getStatus());
        assertNull(paragraph(response).getBusinessTypePrediction().getMaxSimilarity());
        assertEquals(1, response.getPredictionSummary().getFailedCount());
        assertTrue(response.getWarnings().get(0).contains("1个变化段落"));
    }

    @Test
    public void shouldExposeFallbackFailureConsistently() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.success(noMatch())))
                .thenReturn(Collections.singletonList(
                        LenientPrediction.failed("EMBEDDING_UNAVAILABLE")));
        ContractCompareResponse response = response(ChangeType.MODIFIED,
                "旧内容", "新内容", new ClauseContext("旧条款上下文", "新条款上下文"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        assertEquals("FAILED", paragraph(response).getBusinessTypePrediction().getStatus());
        assertEquals("NEW_CONTEXT", paragraph(response).getBusinessTypePrediction().getInputScope());
        assertTrue(paragraph(response).getBusinessTypePrediction().isFallbackUsed());
        assertNull(paragraph(response).getBusinessTypePrediction().getMatchType());
        assertEquals("v1", paragraph(response).getBusinessTypePrediction().getModelVersion());
    }

    @Test
    public void shouldSkipOverlongBaseParagraphWithoutCallingPrediction() {
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setModelVersion("v1");
        properties.getSearch().setMaxParagraphLength(4);
        service = new ContractComparePredictionService(paragraphService, properties);
        ContractCompareResponse response = response(ChangeType.ADDED,
                null, "超过四个字符", new ClauseContext(null, "超过四个字符"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        assertEquals("SKIPPED_TOO_LONG",
                paragraph(response).getBusinessTypePrediction().getStatus());
        assertNull(paragraph(response).getBusinessTypePrediction().getMaxSimilarity());
        assertEquals(0, response.getPredictionSummary().getFailedCount());
        assertEquals(1, response.getPredictionSummary().getSkippedTooLongCount());
        verify(paragraphService, never()).predictBatchLenient(anyList(), eq("u1"));
    }

    @Test
    public void shouldDeduplicateIdenticalPrimaryParagraphs() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(
                        LenientPrediction.success(candidateMatch())));
        ContractCompareResponse response = responseWithParagraphs(
                paragraph(ChangeType.ADDED, null, "相同新增内容"),
                paragraph(ChangeType.ADDED, null, "相同新增内容"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        verify(paragraphService).predictBatchLenient(
                Collections.singletonList("相同新增内容"), "u1");
        assertEquals("MATCHED", response.getChanges().get(0).getChangedParagraphs()
                .get(0).getBusinessTypePrediction().getStatus());
        assertEquals("MATCHED", response.getChanges().get(0).getChangedParagraphs()
                .get(1).getBusinessTypePrediction().getStatus());
    }

    @Test
    public void shouldBuildTargetSpecificFallbackForMultipleChangesInOneClause() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Arrays.asList(LenientPrediction.success(noMatch()),
                        LenientPrediction.success(noMatch())))
                .thenReturn(Arrays.asList(LenientPrediction.success(candidateMatch()),
                        LenientPrediction.success(candidateMatch())));
        ContractCompareResponse response = responseWithParagraphs(
                paragraph(ChangeType.MODIFIED, "旧金额", "新金额"),
                paragraph(ChangeType.MODIFIED, "旧期限", "新期限"));
        response.getChanges().get(0).setContext(new ClauseContext(
                "第二条 金额与期限 旧金额 旧期限 其他约定",
                "第二条 金额与期限 新金额 新期限 其他约定"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(paragraphService, times(2)).predictBatchLenient(captor.capture(), eq("u1"));
        List<String> fallbackInputs = captor.getAllValues().get(1);
        assertEquals(2, fallbackInputs.size());
        assertNotEquals(fallbackInputs.get(0), fallbackInputs.get(1));
        assertTrue(fallbackInputs.get(0).contains("新金额"));
        assertTrue(fallbackInputs.get(1).contains("新期限"));
    }

    @Test
    public void shouldKeepFallbackWithinLimitAndRetainTargetParagraph() {
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setModelVersion("v1");
        properties.getSearch().setMaxParagraphLength(30);
        service = new ContractComparePredictionService(paragraphService, properties);
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.success(noMatch())))
                .thenReturn(Collections.singletonList(
                        LenientPrediction.success(candidateMatch())));
        ContractCompareResponse response = response(ChangeType.MODIFIED,
                "旧目标", "新目标", new ClauseContext(
                        "第二条 金额与期限 很长的旧前置约定旧目标很长的旧后置约定",
                        "第二条 金额与期限 很长的新前置约定新目标很长的新后置约定"));

        service.predict(response, AnalysisType.DOUBLE_VERSION, "u1");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(paragraphService, times(2)).predictBatchLenient(captor.capture(), eq("u1"));
        String fallbackInput = captor.getAllValues().get(1).get(0);
        assertTrue(fallbackInput.length() <= 30);
        assertTrue(fallbackInput.contains("新目标"));
    }

    @Test
    public void shouldComposeChangeDocumentFallbackFromHeadingTargetAndNewContent() {
        when(paragraphService.predictBatchLenient(anyList(), eq("u1")))
                .thenReturn(Collections.singletonList(LenientPrediction.success(noMatch())))
                .thenReturn(Collections.singletonList(
                        LenientPrediction.success(candidateMatch())));
        ChangedParagraph changed = paragraph(ChangeType.MODIFIED, "旧金额", "新金额");
        ClauseChange change = new ClauseChange();
        change.setClauseTitle("合同金额");
        change.setSourceHeading("1、《基金合同》第二条约定如下");
        change.setTargetClauseReference("《基金合同》第二条");
        change.setChangeType(ChangeType.MODIFIED);
        change.setChangedParagraphs(Collections.singletonList(changed));
        ContractCompareResponse response = new ContractCompareResponse(
                1, Collections.singletonList(change), Collections.<String>emptyList());

        service.predict(response, AnalysisType.CHANGE_DOCUMENT, "u1");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(paragraphService, times(2)).predictBatchLenient(captor.capture(), eq("u1"));
        String fallbackInput = captor.getAllValues().get(1).get(0);
        assertTrue(fallbackInput.contains("1、《基金合同》第二条约定如下"));
        assertTrue(fallbackInput.contains("《基金合同》第二条"));
        assertTrue(fallbackInput.contains("新金额"));
        assertEquals("NEW_CONTEXT", changed.getBusinessTypePrediction().getInputScope());
    }

    private ContractCompareResponse response(ChangeType type, String oldText, String newText,
                                             ClauseContext context) {
        ChangedParagraph paragraph = new ChangedParagraph();
        paragraph.setParagraphChangeType(type);
        paragraph.setOldContent(oldText);
        paragraph.setNewContent(newText);
        ClauseChange change = new ClauseChange();
        change.setClauseNo("第二条");
        change.setClauseTitle("金额");
        change.setChangeType(type);
        change.setContext(context);
        change.setChangedParagraphs(Collections.singletonList(paragraph));
        return new ContractCompareResponse(1, Collections.singletonList(change),
                Collections.<String>emptyList());
    }

    private ChangedParagraph paragraph(ContractCompareResponse response) {
        return response.getChanges().get(0).getChangedParagraphs().get(0);
    }

    private ChangedParagraph paragraph(ChangeType type, String oldText, String newText) {
        ChangedParagraph paragraph = new ChangedParagraph();
        paragraph.setParagraphChangeType(type);
        paragraph.setOldContent(oldText);
        paragraph.setNewContent(newText);
        return paragraph;
    }

    private ContractCompareResponse responseWithParagraphs(ChangedParagraph... paragraphs) {
        ClauseChange change = new ClauseChange();
        change.setClauseNo("第二条");
        change.setClauseTitle("金额与期限");
        change.setChangeType(ChangeType.MODIFIED);
        change.setChangedParagraphs(Arrays.asList(paragraphs));
        return new ContractCompareResponse(1, Collections.singletonList(change),
                Collections.<String>emptyList());
    }

    private PredictionResponse noMatch() {
        return new PredictionResponse("NO_RELIABLE_MATCH", "v1", 0.50D,
                Collections.<ChangeTypePrediction>emptyList(), Collections.emptyList());
    }

    private PredictionResponse highMatch() {
        return new PredictionResponse("SEMANTIC", "v1", 0.91D,
                Collections.singletonList(new ChangeTypePrediction("20", 0.85D, 3, "HIGH")),
                Collections.emptyList());
    }

    private PredictionResponse candidateMatch() {
        return new PredictionResponse("SEMANTIC", "v1", 0.78D,
                Collections.singletonList(new ChangeTypePrediction("25", 0.60D, 1, "CANDIDATE")),
                Collections.emptyList());
    }
}
