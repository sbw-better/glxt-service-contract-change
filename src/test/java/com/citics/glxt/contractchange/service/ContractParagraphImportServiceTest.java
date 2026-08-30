package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.model.ImportResponse;
import com.citics.glxt.contractchange.util.HashUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContractParagraphImportServiceTest {
    private ContractParagraphMapper mapper;
    private ContractParagraphPersistenceService persistenceService;
    private ParagraphVectorIndexService indexService;
    private EmbeddingClient embeddingClient;
    private ContractChangeProperties properties;
    private ContractParagraphImportService service;

    @Before
    public void setUp() {
        mapper = mock(ContractParagraphMapper.class);
        persistenceService = mock(ContractParagraphPersistenceService.class);
        indexService = mock(ParagraphVectorIndexService.class);
        embeddingClient = mock(EmbeddingClient.class);
        properties = new ContractChangeProperties();
        properties.getEmbedding().setDimension(3);
        properties.getEmbedding().setModelVersion("test-v1");
        properties.getEmbedding().setBatchSize(2);
        service = new ContractParagraphImportService(mapper, persistenceService,
                indexService, embeddingClient, properties);
    }

    @Test
    public void shouldImportValidExcelAndCanonicalizeCodes() throws Exception {
        when(mapper.selectByTextHashes(anyList())).thenReturn(Collections.emptyList());
        when(mapper.countAllParagraphs()).thenReturn(0);
        when(embeddingClient.embed(anyList(), anyString())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<float[]>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(new float[]{1F, 0F, 0F});
            }
            return new EmbeddingBatchResult(3, vectors);
        });
        MockMultipartFile file = excel(new String[][]{{"历史段落A", "TYPE02;TYPE01"}});

        ImportResponse response = service.importExcel(file, "test-user");

        assertTrue(response.isSuccess());
        assertEquals(1, response.getInserted());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContractParagraphDO>> captor =
                (ArgumentCaptor<List<ContractParagraphDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(persistenceService).saveAll(captor.capture());
        ContractParagraphDO saved = captor.getValue().get(0);
        assertEquals("TYPE01;TYPE02", saved.getChangeTypeCodes());
        assertEquals(12, saved.getVectorData().length);
    }

    /** 停用记录使用Excel重新导入时更新原行并重新启用，不触发唯一Hash冲突。 */
    @Test
    public void shouldUpdateAndEnableDisabledParagraph() throws Exception {
        ContractParagraphDO existing = new ContractParagraphDO();
        existing.setId(99L);
        existing.setTextHash(HashUtils.sha256("历史段落A"));
        existing.setChangeTypeCodes("TYPE01;TYPE02");
        existing.setEnabled(0);
        existing.setModelVersion("old-v1");
        existing.setVectorDim(3);
        when(mapper.selectByTextHashes(anyList())).thenReturn(Collections.singletonList(existing));
        when(embeddingClient.embed(anyList(), anyString())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1F, 0F, 0F})));

        ImportResponse response = service.importExcel(
                excel(new String[][]{{"历史段落A", "TYPE02;TYPE01"}}), "test-user");

        assertTrue(response.isSuccess());
        assertEquals(0, response.getInserted());
        assertEquals(1, response.getUpdated());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContractParagraphDO>> captor =
                (ArgumentCaptor<List<ContractParagraphDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(persistenceService).saveAll(captor.capture());
        assertEquals(Long.valueOf(99L), captor.getValue().get(0).getId());
        assertEquals(Integer.valueOf(1), captor.getValue().get(0).getEnabled());
        assertEquals("TYPE01;TYPE02", captor.getValue().get(0).getChangeTypeCodes());
    }

    @Test
    public void shouldRejectDuplicateParagraphWithDifferentCodes() throws Exception {
        MockMultipartFile file = excel(new String[][]{
                {"同一段落", "TYPE01"}, {"同一段落", "TYPE02"}
        });

        ImportResponse response = service.importExcel(file, "test-user");

        assertFalse(response.isSuccess());
        assertEquals(1, response.getErrors().size());
        verify(embeddingClient, never()).embed(anyList(), anyString());
    }

    @Test
    public void shouldRejectDisabledParagraphWithDifferentDatabaseCodes() throws Exception {
        ContractParagraphDO existing = new ContractParagraphDO();
        existing.setId(100L);
        existing.setTextHash(HashUtils.sha256("历史段落A"));
        existing.setChangeTypeCodes("TYPE01");
        existing.setEnabled(0);
        existing.setModelVersion("old-v1");
        existing.setVectorDim(3);
        when(mapper.selectByTextHashes(anyList())).thenReturn(Collections.singletonList(existing));

        ImportResponse response = service.importExcel(
                excel(new String[][]{{"历史段落A", "TYPE02"}}), "test-user");

        assertFalse(response.isSuccess());
        assertEquals(1, response.getErrors().size());
        verify(embeddingClient, never()).embed(anyList(), anyString());
        verify(persistenceService, never()).saveAll(anyList());
    }

    @Test
    public void shouldRejectExcelWithoutDataRows() throws Exception {
        ImportResponse response = service.importExcel(excel(new String[][]{}), "test-user");

        assertFalse(response.isSuccess());
        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getMessage().contains("没有有效数据行"));
        verify(embeddingClient, never()).embed(anyList(), anyString());
        verify(persistenceService, never()).saveAll(anyList());
    }

    private MockMultipartFile excel(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("历史样本");
            sheet.createRow(0).createCell(0).setCellValue("合同段落");
            sheet.getRow(0).createCell(1).setCellValue("变更类型编码");
            for (int i = 0; i < rows.length; i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(rows[i][0]);
                row.createCell(1).setCellValue(rows[i][1]);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "samples.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
