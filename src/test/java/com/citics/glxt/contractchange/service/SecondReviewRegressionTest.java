package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.domain.SampleLibraryControlDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.mapper.SampleLibraryMapper;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.util.HashUtils;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class SecondReviewRegressionTest {
    @Test public void incrementalImportMustRejectWrongDimensionBeforeEmbedding() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        ContractParagraphDO existing=row(); existing.setVectorDim(2);
        when(mapper.selectByTextHashes(anyList(),anyString())).thenReturn(Collections.singletonList(existing));
        EmbeddingClient embedding=mock(EmbeddingClient.class);
        when(embedding.embed(anyList(),anyString())).thenReturn(new com.citics.glxt.contractchange.model.EmbeddingBatchResult(3,Collections.singletonList(new float[]{1,0,0})));
        ContractParagraphPersistenceService persistence=mock(ContractParagraphPersistenceService.class);
        ContractParagraphImportService importer=new ContractParagraphImportService(mapper,persistence,mock(ParagraphVectorIndexService.class),embedding,properties());
        expect409(() -> importer.importPrepared(parsed(),"user",null,null));
        verifyZeroInteractions(embedding,persistence);
    }

    @Test public void duplicateDatabaseHashMustNotSilentlyOverwriteLookup() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        when(mapper.selectByTextHashes(anyList(),anyString())).thenReturn(Arrays.asList(row(),row()));
        ContractParagraphImportService importer=new ContractParagraphImportService(mapper,null,mock(ParagraphVectorIndexService.class),null,properties());
        expect409(() -> importer.importPrepared(parsed(),"user",null,null));
    }

    @Test public void wrongDimensionRowsMustNotBeReportedAsNormalEmptyLibrary() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        ContractParagraphDO wrongDimension=row(); wrongDimension.setVectorDim(2);
        when(mapper.selectActiveParagraphs("v1",3)).thenReturn(Collections.singletonList(wrongDimension));
        ParagraphVectorIndexService index=new ParagraphVectorIndexService(mapper,properties());
        index.reload();
        assertEquals("DEGRADED",index.status().getStatus());
        assertEquals(1,index.status().getErrorCount()); assertFalse(index.status().isRecognitionAvailable());
    }

    @Test public void malformedControlStateMustDisableRecognition() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("v1",3)).thenReturn(Collections.singletonList(row()));
        ParagraphVectorIndexService index=new ParagraphVectorIndexService(mapper,properties()); index.reload();
        SampleLibraryStore store=mock(SampleLibraryStore.class);
        SampleLibraryControlDO value=control(); value.setOperationStatus(null);
        when(store.control()).thenReturn(value); index.setStore(store);
        assertFalse(index.status().isRecognitionAvailable());
        try { index.beginPrediction(); fail(); } catch(ContractChangeBusinessException ex) { assertEquals(503,ex.getCode()); }
    }

    @Test public void incrementalCommitMustRejectNonRunningOwnerBeforeSaving() {
        SampleLibraryMapper mapper=mock(SampleLibraryMapper.class);
        SampleLibraryControlDO value=control(); value.setOperationId("op"); value.setOperationStatus("DB_COMMITTED"); value.setExpectedSamples(1);
        when(mapper.lockControl()).thenReturn(value); when(mapper.switchVersion(any())).thenReturn(1);
        ContractParagraphPersistenceService persistence=mock(ContractParagraphPersistenceService.class);
        SampleLibraryStore store=new SampleLibraryStore(mapper,mock(ContractParagraphMapper.class),persistence);
        expect409(() -> store.commitIncremental(Collections.singletonList(row()),value,properties().getEmbedding()));
        verifyZeroInteractions(persistence);
    }

    @Test public void committedRecoveryCannotBypassExpectedCountWithNullArgument() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("v1",3)).thenReturn(Collections.singletonList(row()));
        SampleLibraryStore store=mock(SampleLibraryStore.class); SampleLibraryControlDO value=control();
        value.setOperationId("op"); value.setOperationStatus("DB_COMMITTED"); value.setExpectedSamples(2);
        when(store.control()).thenReturn(value);
        ParagraphVectorIndexService index=new ParagraphVectorIndexService(mapper,properties()); index.setStore(store);
        try { index.reloadForOperation("op",null); fail("partial committed index published"); }
        catch(ContractChangeBusinessException ex) { assertEquals(503,ex.getCode()); }
        verify(store,never()).complete(anyString());
        assertEquals(0,index.status().getSampleCount());
    }

    private ContractParagraphImportService.ParsedExcel parsed() {
        ContractParagraphImportService.ParsedExcel parsed=new ContractParagraphImportService.ParsedExcel(); parsed.totalRows=1;
        String hash=HashUtils.sha256("条款");
        parsed.rows.put(hash,new ContractParagraphImportService.PreparedRow(2,"条款","条款",hash,"49")); return parsed;
    }
    private ContractParagraphDO row() {
        ContractParagraphDO value=new ContractParagraphDO(); value.setId(1L); value.setOriginalText("条款");value.setNormalizedText("条款");
        value.setTextHash(HashUtils.sha256("条款"));value.setChangeTypeCodes("49");value.setModelVersion("v1");value.setVectorDim(3);value.setEnabled(1);value.setVectorData("[1,0,0]");return value;
    }
    private SampleLibraryControlDO control() {
        SampleLibraryControlDO value=new SampleLibraryControlDO();value.setModelName("model");value.setModelVersion("v1");value.setVectorDim(3);value.setOperationStatus("IDLE");return value;
    }
    private ContractChangeProperties properties() {
        ContractChangeProperties value=new ContractChangeProperties();value.getEmbedding().setModelName("model");value.getEmbedding().setModelVersion("v1");value.getEmbedding().setDimension(3);return value;
    }
    private void expect409(Runnable action) {
        try { action.run(); fail("expected conflict"); }
        catch(ContractChangeBusinessException ex) { assertEquals(409,ex.getCode()); }
    }
}
