package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogDO;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisMatchLogDO;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisParagraphLogDO;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.BusinessTypePrediction;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.mapper.AnalysisLogMapper;
import com.citics.glxt.contractchange.mapper.AnalysisMatchLogMapper;
import com.citics.glxt.contractchange.mapper.AnalysisParagraphLogMapper;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionReference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnalysisResultPersistenceServiceTest {
    private AnalysisLogMapper mainMapper;
    private AnalysisParagraphLogMapper paragraphMapper;
    private AnalysisMatchLogMapper matchMapper;
    private AnalysisResultPersistenceService service;

    @Before
    public void setUp() {
        mainMapper = mock(AnalysisLogMapper.class);
        paragraphMapper = mock(AnalysisParagraphLogMapper.class);
        matchMapper = mock(AnalysisMatchLogMapper.class);
        service = new AnalysisResultPersistenceService(mainMapper, paragraphMapper, matchMapper);
        when(mainMapper.updateLog(any(AnalysisLogDO.class))).thenReturn(1);
        doAnswer(invocation -> {
            AnalysisParagraphLogDO value = invocation.getArgument(0);
            value.setId(81L);
            return 1;
        }).when(paragraphMapper).insertLog(any(AnalysisParagraphLogDO.class));
        when(matchMapper.insertLog(any(AnalysisMatchLogDO.class))).thenReturn(1);
    }

    @Test
    public void shouldSaveParagraphSourcePredictionAndTopKReference() {
        AnalysisLogDO main = new AnalysisLogDO();
        main.setAnalysisId("analysis-1");
        ChangedParagraph paragraph = new ChangedParagraph();
        paragraph.setParagraphChangeType(ChangeType.DELETED);
        paragraph.setOldContent("删除前内容");
        paragraph.setNewContent(null);
        BusinessTypePrediction prediction = new BusinessTypePrediction();
        prediction.setStatus("MATCHED");
        prediction.setInputScope("OLD_CONTEXT");
        prediction.setFallbackUsed(true);
        prediction.setMatchType("SEMANTIC");
        prediction.setMaxSimilarity(0.91D);
        prediction.setChangeTypes(Collections.singletonList(
                new ChangeTypePrediction("19", 0.9D, 2, "HIGH")));
        prediction.setReferences(Collections.singletonList(
                new PredictionReference(7L, "历史内容", 0.91D, Arrays.asList("19", "04"))));
        paragraph.setBusinessTypePrediction(prediction);
        ClauseChange change = new ClauseChange();
        change.setClauseNo("第二条");
        change.setClauseTitle("金额");
        change.setParentClauseNo("第二部分");
        change.setSourceHeading("来源标题");
        change.setTargetClauseReference("目标第二条");
        change.setChangedParagraphs(Collections.singletonList(paragraph));
        ContractCompareResponse response = new ContractCompareResponse(1,
                Collections.singletonList(change), Collections.<String>emptyList());

        service.persistSuccess(main, response, AnalysisType.CHANGE_DOCUMENT);

        ArgumentCaptor<AnalysisParagraphLogDO> paragraphLog =
                ArgumentCaptor.forClass(AnalysisParagraphLogDO.class);
        verify(paragraphMapper).insertLog(paragraphLog.capture());
        assertEquals("删除前内容", paragraphLog.getValue().getPredictionText());
        assertEquals("OLD_CONTEXT", paragraphLog.getValue().getInputScope());
        assertEquals("第二部分", paragraphLog.getValue().getParentClauseNo());
        assertEquals("来源标题", paragraphLog.getValue().getSourceHeading());
        assertEquals("目标第二条", paragraphLog.getValue().getTargetClauseReference());
        assertEquals(Integer.valueOf(1), paragraphLog.getValue().getFallbackUsed());

        ArgumentCaptor<AnalysisMatchLogDO> matchLog =
                ArgumentCaptor.forClass(AnalysisMatchLogDO.class);
        verify(matchMapper).insertLog(matchLog.capture());
        assertEquals(Long.valueOf(81L), matchLog.getValue().getParagraphLogId());
        assertEquals(Long.valueOf(7L), matchLog.getValue().getHistoricalSampleId());
        assertEquals("19;04", matchLog.getValue().getChangeTypeCodes());
        assertEquals(Integer.valueOf(1), matchLog.getValue().getRankNo());
        verify(mainMapper).updateLog(main);
    }

    @Test
    public void shouldFailWhenAnyMapperDoesNotWriteExactlyOneRow() {
        when(paragraphMapper.insertLog(any(AnalysisParagraphLogDO.class))).thenReturn(0);
        ClauseChange change = new ClauseChange();
        ChangedParagraph paragraph = new ChangedParagraph();
        paragraph.setParagraphChangeType(ChangeType.ADDED);
        paragraph.setNewContent("新增内容");
        change.setChangedParagraphs(Collections.singletonList(paragraph));
        ContractCompareResponse response = new ContractCompareResponse(1,
                Collections.singletonList(change), Collections.<String>emptyList());
        AnalysisLogDO main = new AnalysisLogDO();
        main.setAnalysisId("analysis-1");

        try {
            service.persistSuccess(main, response, AnalysisType.DOUBLE_VERSION);
            fail("zero affected rows should fail");
        } catch (ContractChangeBusinessException expected) {
            // Spring rolls back the final transaction when proxied in the application.
        }
    }

    @Test
    public void shouldDeclareRequiredTransactionBoundaries() throws Exception {
        Method create = AnalysisResultPersistenceService.class
                .getMethod("createProcessing", AnalysisLogDO.class);
        Method finish = AnalysisResultPersistenceService.class.getMethod("persistSuccess",
                AnalysisLogDO.class, ContractCompareResponse.class, AnalysisType.class);
        Method failMethod = AnalysisResultPersistenceService.class.getMethod("markFailed",
                String.class, long.class, String.class);

        assertEquals(Propagation.REQUIRES_NEW,
                create.getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.REQUIRED,
                finish.getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.REQUIRES_NEW,
                failMethod.getAnnotation(Transactional.class).propagation());
        assertNotNull(finish.getAnnotation(Transactional.class));
    }
}
