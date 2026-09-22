package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.ResultMode;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractor;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractorRegistry;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 /export 使用策略提取且保持原有详细结果与 Excel 格式。 */
public class ContractCompareServiceTest {
    @Test
    public void shouldValidateDefaultExportFilePathsInDoubleVersionStrategy() {
        ContractCompareRequest request = new ContractCompareRequest();

        try {
            service(mock(SftpContractFileLoader.class)).compare(request);
            fail("old file path should be required");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("修改前合同文件路径"));
        }
    }

    @Test
    public void shouldReturnDetailedComparisonAndTrimSimpleContext() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币120万元。"));
        ContractCompareService service = service(loader);
        ContractCompareRequest request = doubleVersionRequest();

        ContractCompareResponse response = service.compare(request);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0)
                .getOldContent().contains("100万元"));
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0)
                .getNewContent().contains("120万元"));
        assertNull(response.getChanges().get(0).getContext());
    }

    @Test
    public void shouldKeepContextColumnsForContextExport() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "前置约定不变。", "金额为100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "前置约定不变。", "金额为120万元。"));
        ContractCompareService service = service(loader);
        ContractCompareRequest request = doubleVersionRequest();
        request.setResultMode(ResultMode.CONTEXT);

        ContractCompareResponse response = service.compare(request);
        assertTrue(response.getChanges().get(0).getContext().getOldContent()
                .contains("前置约定不变。"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(service.exportExcel(request)))) {
            assertEquals(11, workbook.getSheetAt(0).getRow(1).getLastCellNum());
            assertEquals("完整变更前条款",
                    workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
        }
    }

    @Test
    public void shouldKeepSimpleExportShapeWithoutPredictionColumns() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document("第二条 金额", "金额为100万元。"));
        when(loader.load("/new.docx")).thenReturn(document("第二条 金额", "金额为120万元。"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                service(loader).exportExcel(doubleVersionRequest())))) {
            assertEquals("比对结果", workbook.getSheetAt(0).getSheetName());
            assertEquals(9, workbook.getSheetAt(0).getRow(1).getLastCellNum());
            assertEquals("修改", workbook.getSheetAt(0).getRow(2).getCell(4).getStringCellValue());
        }
    }

    @Test
    public void shouldRouteChangeDocumentAndKeepItsExportShape() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/supplement.docx")).thenReturn(document(
                "1、《基金合同》第二条“合同金额”约定如下：",
                "合同金额为100万元。", "上述内容变更如下：", "合同金额为120万元。"));
        ContractCompareService service = service(loader);
        ContractCompareRequest request = new ContractCompareRequest();
        request.setAnalysisType(AnalysisType.CHANGE_DOCUMENT);
        request.setChangeFileGetPath("/supplement.docx");
        request.setResultMode(ResultMode.CONTEXT);

        ContractCompareResponse response = service.compare(request);
        assertEquals(1, response.getTotalChanges());
        assertNull(response.getChanges().get(0).getContext());
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(service.exportExcel(request)))) {
            assertEquals("提取结果", workbook.getSheetAt(0).getSheetName());
            assertEquals(8, workbook.getSheetAt(0).getRow(1).getLastCellNum());
        }
    }

    private ContractCompareService service(SftpContractFileLoader loader) {
        AsposeCompareService aspose = new AsposeCompareService();
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());
        ContractChangeExtractor doubleVersion = new DoubleVersionChangeExtractor(loader, aspose,
                new ContractStructureParser(), engine);
        ContractChangeExtractor changeDocument = new ChangeDocumentExtractor(loader,
                new ChangeDocumentExtractionService(aspose, engine));
        return new ContractCompareService(new ContractChangeExtractorRegistry(
                Arrays.asList(doubleVersion, changeDocument)));
    }

    private ContractCompareRequest doubleVersionRequest() {
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");
        return request;
    }

    private byte[] document(String... paragraphs) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        for (String paragraph : paragraphs) {
            builder.writeln(paragraph);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output, SaveFormat.DOCX);
        return output.toByteArray();
    }
}
