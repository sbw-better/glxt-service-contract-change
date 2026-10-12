package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.*;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.mapper.*;
import com.citics.glxt.contractchange.model.*;
import com.citics.glxt.contractchange.util.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 模型全部使用固定向量替身；数据库事务另有真实隔离Oracle测试。 */
public class SampleLibraryRebuildTest {
    private ContractChangeProperties properties;
    private ContractParagraphMapper paragraphs;
    private SampleLibraryMapper library;
    private SampleLibraryStore store;
    private EmbeddingClient embedding;
    private ContractParagraphPersistenceService persistence;
    private ParagraphVectorIndexService index;
    private SampleLibraryImportService service;
    private SampleLibraryControlDO control;
    private com.citics.glxt.contractchange.contractcompare.service.SftpContractFileLoader fileLoader;

    @Before public void setup() {
        properties=new ContractChangeProperties();
        properties.getEmbedding().setModelName("new-model");
        properties.getEmbedding().setModelVersion("new-v2"); properties.getEmbedding().setDimension(3);
        properties.getEmbedding().setBatchSize(1);
        paragraphs=mock(ContractParagraphMapper.class); library=mock(SampleLibraryMapper.class);
        persistence=mock(ContractParagraphPersistenceService.class); embedding=mock(EmbeddingClient.class);
        store=new SampleLibraryStore(library,paragraphs,persistence);
        index=new ParagraphVectorIndexService(paragraphs,properties); index.setStore(store);
        control=new SampleLibraryControlDO(); control.setModelName("old-model");
        control.setModelVersion("old-v1"); control.setVectorDim(3); control.setRevision(7);
        control.setOperationStatus("IDLE");
        when(library.control()).thenReturn(control); when(library.lockControl()).thenReturn(control);
        when(library.claim(anyString())).thenReturn(1); when(library.release(anyString())).thenAnswer(inv -> {
            if (!Objects.equals(control.getOperationId(),inv.getArgument(0)) || !"RUNNING".equals(control.getOperationStatus())) return 0;
            control.setOperationId(null); control.setOperationStatus("IDLE"); control.setExpectedSamples(null); return 1;
        });
        when(library.switchVersion(any())).thenAnswer(inv -> { control.setRevision(control.getRevision()+1); return 1; });
        when(library.committed(anyString(),anyInt())).thenAnswer(inv -> {
            control.setOperationStatus("DB_COMMITTED"); control.setExpectedSamples(inv.getArgument(1)); return 1;
        });
        when(library.complete(anyString())).thenAnswer(inv -> {
            if (!Objects.equals(control.getOperationId(),inv.getArgument(0)) || !"DB_COMMITTED".equals(control.getOperationStatus())) return 0;
            control.setOperationId(null); control.setOperationStatus("IDLE"); control.setExpectedSamples(null); return 1;
        });
        when(paragraphs.selectAllActiveParagraphs()).thenReturn(Collections.singletonList(row("历史条款","49","old-v1")));
        when(paragraphs.disableActiveParagraphs("old-v1")).thenReturn(1);
        when(embedding.embed(anyList(),anyString())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1,0,0})));
        when(paragraphs.selectActiveParagraphs("new-v2",3)).thenAnswer(inv -> Arrays.asList(
                row("历史条款","49","new-v2"),row("新增条款","71","new-v2")));
        ContractParagraphImportService importer=new ContractParagraphImportService(paragraphs,persistence,index,embedding,properties);
        fileLoader=mock(com.citics.glxt.contractchange.contractcompare.service.SftpContractFileLoader.class);
        service=new SampleLibraryImportService(importer,paragraphs,store,index,properties,fileLoader);
    }

    @Test public void pathImportMustReadServerExcelAndPassBodyUserId() throws Exception {
        when(fileLoader.loadExcel("/uploads/samples.xlsx")).thenReturn(excel("新增条款","71").getBytes());
        SampleImportRequest request=new SampleImportRequest();
        request.setFileGetPath("/uploads/samples.xlsx"); request.setUserId("body-user");
        SampleRebuildResponse result=(SampleRebuildResponse)service.importFromPath(request);
        assertTrue(result.isSuccess()); verify(fileLoader).loadExcel("/uploads/samples.xlsx");
        verify(embedding,times(2)).embed(anyList(),eq("body-user"));
    }

    @Test public void downloadFailureMustNotClaimLibraryOrCallModel() {
        when(fileLoader.loadExcel(anyString())).thenThrow(new ContractChangeBusinessException("Excel文件下载失败"));
        SampleImportRequest request=new SampleImportRequest(); request.setFileGetPath("/missing.xlsx"); request.setUserId("user");
        try { service.importFromPath(request); fail(); } catch (ContractChangeBusinessException expected) {}
        verify(library,never()).claim(anyString()); verifyZeroInteractions(embedding);
    }

    @Test public void oversizedServerExcelMustBeRejectedBeforeClaim() {
        when(fileLoader.loadExcel(anyString())).thenReturn(new byte[20*1024*1024+1]);
        SampleImportRequest request=new SampleImportRequest(); request.setFileGetPath("/big.xlsx"); request.setUserId("user");
        try { service.importFromPath(request); fail(); } catch (ContractChangeBusinessException expected) {
            assertEquals("Excel文件不能超过20MB",expected.getMessage());
        }
        verify(library,never()).claim(anyString()); verifyZeroInteractions(embedding);
    }

    @Test public void rebuildMustFinishBeforeReturnAndReembedAllRows() throws Exception {
        SampleRebuildResponse response=(SampleRebuildResponse)service.importExcel(excel("新增条款","71"),"audit-user");
        assertTrue(response.isSuccess()); assertNotNull(response.getOperationId()); assertEquals("IDLE",control.getOperationStatus()); assertEquals(2,response.getInsertedSamples());
        assertEquals(1,response.getDisabledSamples()); assertEquals("new-v2",control.getModelVersion());
        assertNull(control.getOperationId()); assertTrue(index.status().isRecognitionAvailable());
        verify(embedding,times(2)).embed(anyList(),eq("audit-user"));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<ContractParagraphDO>> saved=(ArgumentCaptor)ArgumentCaptor.forClass(List.class);
        verify(persistence).saveAll(saved.capture());
        for (ContractParagraphDO row:saved.getValue()) {
            assertNull(row.getId()); assertEquals("new-v2",row.getModelVersion());
            assertEquals("[1.0,0.0,0.0]",row.getVectorData());
        }
        assertEquals(2,index.search(new float[]{1,0,0},10).size());
    }

    @Test public void sameTextSameLabelsPreferExcelOriginal() throws Exception {
        when(paragraphs.selectActiveParagraphs("new-v2",3)).thenReturn(Collections.singletonList(row("历史条款","49","new-v2")));
        SampleRebuildResponse response=(SampleRebuildResponse)service.importExcel(excel("历史条款","49"),"audit-user");
        verify(embedding,times(1)).embed(anyList(),anyString());
        assertEquals(1,response.getTotalSamples());
    }

    @Test public void conflictingLabelsFailBeforeAnyModelOrWrite() throws Exception {
        expectCode(409,() -> service.importExcel(excel("历史条款","71"),"u"));
        verifyZeroInteractions(embedding,persistence); assertNull(control.getOperationId());
    }
    @Test public void mixedEffectiveVersionRejected() throws Exception {
        when(paragraphs.selectAllActiveParagraphs()).thenReturn(Collections.singletonList(row("历史条款","49","other")));
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        verifyZeroInteractions(embedding,persistence);
    }
    @Test public void corruptedHistoricalHashRejected() throws Exception {
        ContractParagraphDO bad=row("历史条款","49","old-v1"); bad.setTextHash("wrong");
        when(paragraphs.selectAllActiveParagraphs()).thenReturn(Collections.singletonList(bad));
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        verifyZeroInteractions(embedding,persistence);
    }
    @Test public void mergedCapacityIncludesHistoryNotPhysicalRows() throws Exception {
        properties.getImportConfig().setMaxTotalSamples(1);
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        verifyZeroInteractions(embedding,persistence);
    }
    @Test public void midModelFailureLeavesOldVersionActive() throws Exception {
        when(embedding.embed(anyList(),anyString())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1,0,0}))).thenThrow(new IllegalStateException("private response"));
        expectCode(503,() -> service.importExcel(excel("新增条款","71"),"u"));
        assertEquals("IDLE",control.getOperationStatus()); assertEquals("old-v1",control.getModelVersion());
        assertNull(control.getOperationId());
        verify(persistence,never()).saveAll(anyList());
    }
    @Test public void indexFailureKeepsCommittedStateAndReloadDoesNotEmbedAgain() throws Exception {
        when(paragraphs.selectActiveParagraphs("new-v2",3)).thenThrow(new IllegalStateException());
        expectCode(503,() -> service.importExcel(excel("新增条款","71"),"u"));
        assertEquals("DB_COMMITTED",control.getOperationStatus()); assertEquals(Integer.valueOf(2),control.getExpectedSamples()); assertNotNull(control.getOperationId());
        assertFalse(index.status().isRecognitionAvailable());
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        doReturn(Arrays.asList(row("历史条款","49","new-v2"),row("新增条款","71","new-v2")))
                .when(paragraphs).selectActiveParagraphs("new-v2",3);
        index.reload(); assertEquals("IDLE",control.getOperationStatus()); assertNull(control.getExpectedSamples()); assertNull(control.getOperationId());
        verify(embedding,times(2)).embed(anyList(),anyString()); verify(persistence,times(1)).saveAll(anyList());
    }
    @Test public void strictRebuildIndexDoesNotSkipBadRecord() throws Exception {
        ContractParagraphDO bad=row("新增条款","71","new-v2"); bad.setVectorData("[0,0,0]");
        when(paragraphs.selectActiveParagraphs("new-v2",3)).thenReturn(Arrays.asList(row("历史条款","49","new-v2"),bad));
        expectCode(503,() -> service.importExcel(excel("新增条款","71"),"u"));
        assertEquals("DB_COMMITTED",control.getOperationStatus()); assertFalse(index.status().isRecognitionAvailable());
    }
    @Test public void committedOperationCannotBeReleasedOrCompletedByAnotherWriter() {
        control.setOperationId("committed"); control.setOperationStatus("DB_COMMITTED"); control.setExpectedSamples(2);
        store.release("committed"); expectCode(409,() -> store.complete("other"));
        assertEquals("committed",control.getOperationId()); assertEquals(Integer.valueOf(2),control.getExpectedSamples());
    }
    @Test public void strictRecoveryRejectsMissingRows() {
        control.setModelName("new-model"); control.setModelVersion("new-v2");
        control.setOperationId("committed"); control.setOperationStatus("DB_COMMITTED"); control.setExpectedSamples(2);
        when(paragraphs.selectActiveParagraphs("new-v2",3)).thenReturn(Collections.singletonList(row("历史条款","49","new-v2")));
        expectCode(503,() -> index.reload());
        assertEquals("DB_COMMITTED",control.getOperationStatus()); assertFalse(index.status().isRecognitionAvailable());
        verify(library,never()).complete(anyString());
    }
    @Test public void busyImportAndManualReloadReturnConflict() throws Exception {
        control.setOperationId("ordinary-import");
        control.setOperationStatus("RUNNING");
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        expectCode(409,() -> index.reload());
    }
    @Test public void sameVersionChangedNameOrDimensionRejected() throws Exception {
        properties.getEmbedding().setModelVersion("old-v1");
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        properties.getEmbedding().setModelName("old-model"); properties.getEmbedding().setDimension(4);
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
    }
    @Test public void usedTargetVersionRejected() throws Exception {
        when(library.usedVersion("new-v2")).thenReturn(1);
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        verifyZeroInteractions(embedding,persistence);
    }
    @Test public void missingControlOrUninitializedNonemptyLibraryRejected() throws Exception {
        when(library.lockControl()).thenReturn(null);
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
        when(library.lockControl()).thenReturn(control); control.setModelVersion(null);
        when(paragraphs.countAllParagraphs()).thenReturn(1);
        expectCode(409,() -> service.importExcel(excel("新增条款","71"),"u"));
    }
    @Test public void firstImportIsIncrementalAndSetsIdentity() throws Exception {
        control.setModelVersion(null); control.setModelName(null); control.setVectorDim(null);
        when(paragraphs.selectByTextHashes(anyList(),eq("new-v2"))).thenReturn(Collections.emptyList());
        Object result=service.importExcel(excel("新增条款","71"),"u");
        assertTrue(result instanceof ImportResponse); assertEquals("new-v2",control.getModelVersion());
        assertEquals("IDLE",control.getOperationStatus()); assertNull(control.getOperationId());
    }
    @Test public void startupPrecommitFailsAndReleasesButPostcommitKeepsOperation() {
        control.setOperationStatus("RUNNING");
        control.setOperationId("interrupted"); store.recoverInterrupted();
        assertEquals("IDLE",control.getOperationStatus()); assertNull(control.getOperationId());
        control.setOperationStatus("DB_COMMITTED"); control.setExpectedSamples(2); control.setOperationId("interrupted"); store.recoverInterrupted();
        assertEquals("DB_COMMITTED",control.getOperationStatus()); assertEquals("interrupted",control.getOperationId());
    }
    @Test public void sourceRevisionChangeRejectedBeforePersistence() {
        control.setOperationId("op"); control.setOperationStatus("RUNNING");
        SampleLibraryControlDO source=new SampleLibraryControlDO(); source.setOperationId("op");
        source.setRevision(6); source.setModelVersion("old-v1");
        expectCode(409,() -> store.commitRebuild(source,properties.getEmbedding(),Collections.emptyList(),1));
        verify(persistence,never()).saveAll(anyList());
    }
    @Test public void exactMatchCannotBypassModelCompatibility() {
        when(paragraphs.selectActiveParagraphs("old-v1",3)).thenReturn(Collections.singletonList(row("历史条款","49","old-v1")));
        index.reload(); assertEquals("old-v1",index.status().getModelVersion());
        ContractParagraphPredictionService prediction=new ContractParagraphPredictionService(index,embedding,properties);
        expectCode(503,() -> prediction.predict("历史条款","u"));
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN,new IndexHealthIndicator(index).health().getStatus());
        assertFalse(prediction.predictBatchLenient(Collections.singletonList("历史条款"),"u").get(0).isSuccess());
        verifyZeroInteractions(embedding);
    }

    @Test public void predictionUsesOneSnapshotEvenWhenReloadPublishesReplacement() {
        properties.getEmbedding().setModelName("old-model"); properties.getEmbedding().setModelVersion("old-v1");
        when(paragraphs.selectActiveParagraphs("old-v1",3)).thenReturn(Collections.singletonList(row("历史条款","49","old-v1")));
        index.reload(); index.beginPrediction();
        try {
            when(paragraphs.selectActiveParagraphs("old-v1",3)).thenReturn(Collections.singletonList(row("替换条款","71","old-v1")));
            index.reload();
            assertNotNull(index.exact(HashUtils.sha256("历史条款")));
            assertNull(index.exact(HashUtils.sha256("替换条款")));
            assertEquals("49",index.search(new float[]{1,0,0},10).get(0).getSample().getChangeTypeCodes().get(0));
        } finally { index.endPrediction(); }
        assertNotNull(index.exact(HashUtils.sha256("替换条款")));
    }

    @Test public void startupAfterCommitRestoresIndexWithoutReembedding() throws Exception {
        when(paragraphs.selectActiveParagraphs("new-v2",3)).thenThrow(new IllegalStateException());
        expectCode(503,() -> service.importExcel(excel("新增条款","71"),"u"));
        doReturn(Arrays.asList(row("历史条款","49","new-v2"),row("新增条款","71","new-v2")))
                .when(paragraphs).selectActiveParagraphs("new-v2",3);
        ParagraphVectorIndexService restarted=new ParagraphVectorIndexService(paragraphs,properties); restarted.setStore(store);
        restarted.initialize();
        assertEquals("IDLE",control.getOperationStatus()); assertTrue(restarted.status().isRecognitionAvailable());
        verify(embedding,times(2)).embed(anyList(),anyString()); verify(persistence,times(1)).saveAll(anyList());
    }

    @Test public void failedSameModelReloadKeepsCompatibleSnapshot() {
        properties.getEmbedding().setModelName("old-model"); properties.getEmbedding().setModelVersion("old-v1");
        when(paragraphs.selectActiveParagraphs("old-v1",3)).thenReturn(Collections.singletonList(row("历史条款","49","old-v1")));
        index.reload();
        when(paragraphs.selectActiveParagraphs("old-v1",3)).thenThrow(new IllegalStateException());
        try { index.reload(); fail(); } catch(IllegalStateException expected) { }
        assertTrue(index.status().isRecognitionAvailable());
        assertNotNull(index.exact(HashUtils.sha256("历史条款")));
    }

    private static ContractParagraphDO row(String text,String codes,String version) {
        ContractParagraphDO row=new ContractParagraphDO(); row.setId(1L); row.setOriginalText(text);
        row.setNormalizedText(ContractTextNormalizer.normalize(text)); row.setTextHash(HashUtils.sha256(row.getNormalizedText()));
        row.setChangeTypeCodes(codes); row.setVectorDim(3); row.setModelVersion(version); row.setEnabled(1);
        row.setVectorData("[1.0,0.0,0.0]"); return row;
    }
    private static MockMultipartFile excel(String text,String codes) throws Exception {
        try (XSSFWorkbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet s=wb.createSheet(); s.createRow(0).createCell(0).setCellValue("合同段落");
            s.getRow(0).createCell(1).setCellValue("变更类型编码"); s.createRow(1).createCell(0).setCellValue(text);
            s.getRow(1).createCell(1).setCellValue(codes); wb.write(out);
            return new MockMultipartFile("file","samples.xlsx","application/octet-stream",out.toByteArray());
        }
    }
    private interface Checked { void run() throws Exception; }
    private static void expectCode(int code,Checked action) {
        try { action.run(); fail("Expected code "+code); }
        catch (ContractChangeBusinessException ex) { assertEquals(code,ex.getCode()); assertFalse(ex.getMessage().contains("private response")); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
}
