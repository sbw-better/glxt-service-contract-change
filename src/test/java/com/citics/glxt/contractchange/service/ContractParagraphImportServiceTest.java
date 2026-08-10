package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.model.ImportResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
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
        when(mapper.selectByTextHash(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<float[]>();
            for (int i = 0; i < texts.size(); i++) vectors.add(new float[]{1F, 0F, 0F});
            return new EmbeddingBatchResult(3, vectors);
        });
        MockMultipartFile file = excel(new String[][]{{"历史段落A", "TYPE02;TYPE01"}});

        ImportResponse response = service.importExcel(file);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getInserted());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContractParagraphDO>> captor =
                (ArgumentCaptor<List<ContractParagraphDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(persistenceService).insertAll(captor.capture());
        ContractParagraphDO saved = captor.getValue().get(0);
        assertEquals("TYPE01;TYPE02", saved.getChangeTypeCodes());
        assertEquals(12, saved.getVectorData().length);
    }

    @Test
    public void shouldRejectDuplicateParagraphWithDifferentCodes() throws Exception {
        MockMultipartFile file = excel(new String[][]{
                {"同一段落", "TYPE01"}, {"同一段落", "TYPE02"}
        });

        ImportResponse response = service.importExcel(file);

        assertFalse(response.isSuccess());
        assertEquals(1, response.getErrors().size());
        verify(embeddingClient, never()).embed(anyList());
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
