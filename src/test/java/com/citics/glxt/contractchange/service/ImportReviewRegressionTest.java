package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.util.HashUtils;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 复现本轮检查发现的输入、写入及恢复边界。 */
public class ImportReviewRegressionTest {
    @Test public void invalidOfficeBytesMustReturnBusiness400() {
        ContractParagraphImportService importer = new ContractParagraphImportService(null,null,null,null,new ContractChangeProperties());
        try {
            importer.readExcel("this is not an Excel file".getBytes(StandardCharsets.UTF_8));
            fail("invalid Office content accepted");
        } catch (ContractChangeBusinessException ex) {
            assertEquals(400,ex.getCode());
        }
    }

    @Test public void zeroRowInsertMustFailInsteadOfReportingSaved() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        when(mapper.insertParagraph(any())).thenReturn(0);
        try {
            new ContractParagraphPersistenceService(mapper).saveAll(Collections.singletonList(new ContractParagraphDO()));
            fail("zero row insert accepted");
        } catch (IllegalStateException expected) { }
    }

    @Test public void persistedTextMustBeReadBackAndVerifiedBeforeCommit() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        ContractParagraphDO expected=row("保留原文");
        expected.setId(null);
        when(mapper.insertParagraph(expected)).thenAnswer(invocation -> {
            expected.setId(42L);
            return 1;
        });
        when(mapper.selectTextForVerificationByIds(Collections.singletonList(42L)))
                .thenReturn(Collections.singletonList(expected));

        new ContractParagraphPersistenceService(mapper).saveAll(Collections.singletonList(expected));

        verify(mapper).selectTextForVerificationByIds(Collections.singletonList(42L));
    }

    @Test public void databaseCharacterConversionMustFailWriteVerificationWithSampleId() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        ContractParagraphDO expected=row("包含\u00A0不换行空格");
        expected.setId(null);
        expected.setNormalizedText("包含\u00A0不换行空格");
        expected.setTextHash(HashUtils.sha256(expected.getNormalizedText()));
        when(mapper.insertParagraph(expected)).thenAnswer(invocation -> {
            expected.setId(136L);
            return 1;
        });
        ContractParagraphDO converted=row("包含?不换行空格");
        converted.setId(136L);
        converted.setOriginalText("包含?不换行空格");
        converted.setNormalizedText("包含?不换行空格");
        when(mapper.selectTextForVerificationByIds(Collections.singletonList(136L)))
                .thenReturn(Collections.singletonList(converted));

        try {
            new ContractParagraphPersistenceService(mapper).saveAll(Collections.singletonList(expected));
            fail("database text conversion must be detected before transaction commit");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("ID=136"));
            assertTrue(ex.getMessage().contains("本批已回滚"));
        }
    }

    @Test public void strictRecoveryMustRejectWrongHashDespiteCorrectVectorCount() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        ContractParagraphDO corrupted=row("历史条款"); corrupted.setTextHash(HashUtils.sha256("其他条款"));
        when(mapper.selectActiveParagraphs("v1",3)).thenReturn(Collections.singletonList(corrupted));
        ParagraphVectorIndexService index=index(mapper);
        try { index.reloadForOperation(null,1); fail("corrupted exact index published"); }
        catch (ContractChangeBusinessException expected) { assertEquals(503,expected.getCode()); }
        assertEquals(0,index.status().getSampleCount());
    }

    @Test public void duplicateHashMustNotProduceTwoSemanticSamplesAndOneExactSample() {
        ContractParagraphMapper mapper=mock(ContractParagraphMapper.class);
        ContractParagraphDO first=row("历史条款"), duplicate=row("历史条款"); duplicate.setId(2L);
        when(mapper.selectActiveParagraphs("v1",3)).thenReturn(Arrays.asList(first,duplicate));
        ParagraphVectorIndexService index=index(mapper); index.reload();
        assertEquals("DEGRADED",index.status().getStatus());
        assertEquals(1,index.status().getSampleCount()); assertEquals(1,index.status().getErrorCount());
    }

    @Test public void controlFailureAtStartupMustActuallyMarkLoadFailed() {
        ParagraphVectorIndexService index=index(mock(ContractParagraphMapper.class));
        SampleLibraryStore store=mock(SampleLibraryStore.class); index.setStore(store);
        doThrow(new ContractChangeBusinessException(409,"控制状态异常")).when(store).recoverInterrupted();
        index.initialize();
        assertEquals("LOAD_FAILED",index.status().getStatus());
        assertFalse(index.status().isRecognitionAvailable());
    }

    private ParagraphVectorIndexService index(ContractParagraphMapper mapper) {
        ContractChangeProperties properties=new ContractChangeProperties();
        properties.getEmbedding().setModelVersion("v1"); properties.getEmbedding().setDimension(3);
        return new ParagraphVectorIndexService(mapper,properties);
    }
    private ContractParagraphDO row(String text) {
        ContractParagraphDO row=new ContractParagraphDO(); row.setId(1L); row.setOriginalText(text);
        row.setNormalizedText(text); row.setTextHash(HashUtils.sha256(text)); row.setChangeTypeCodes("49");
        row.setModelVersion("v1"); row.setVectorDim(3); row.setEnabled(1); row.setVectorData("[1.0,0.0,0.0]");
        return row;
    }
}
