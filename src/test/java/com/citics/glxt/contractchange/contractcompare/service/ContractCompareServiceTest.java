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
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.BusinessTypePrediction;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import org.junit.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证从两份 DOCX 字节到条款级响应的完整核心链路。 */
public class ContractCompareServiceTest {
    @Test
    public void shouldReturnOneCompleteModifiedClause() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币120万元。"));

        ContractCompareProperties properties = new ContractCompareProperties();
        AsposeCompareService aspose = new AsposeCompareService();
        ContractCompareService service = new ContractCompareService(loader, aspose,
                new ContractStructureParser(), new ClauseComparisonEngine(properties));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");

        ContractCompareResponse response = service.compare(request);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        ChangedParagraph paragraph = response.getChanges().get(0).getChangedParagraphs().get(0);
        assertTrue(paragraph.getOldContent().contains("100万元"));
        assertTrue(paragraph.getNewContent().contains("120万元"));
        assertEquals("100", paragraph.getChangeDetails().get(0).getOldText());
        assertEquals("120", paragraph.getChangeDetails().get(0).getNewText());
        assertNull(response.getChanges().get(0).getContext());
    }

    @Test
    public void shouldReportChangeUnderItsChinesePartHeading() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第一部分 总则", "本部分内容保持不变。",
                "第二部分 投资范围", "（一）投资品种", "投资品种保持不变。",
                "（二）比例限制", "投资比例不超过20%。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第一部分 总则", "本部分内容保持不变。",
                "第二部分 投资范围", "（一）投资品种", "投资品种保持不变。",
                "（二）比例限制", "投资比例不超过30%。"));
        ContractCompareService service = new ContractCompareService(loader,
                new AsposeCompareService(), new ContractStructureParser(),
                new ClauseComparisonEngine(new ContractCompareProperties()));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");

        ContractCompareResponse response = service.compare(request);

        assertEquals(1, response.getTotalChanges());
        assertEquals("（二）", response.getChanges().get(0).getClauseNo());
        assertEquals("比例限制", response.getChanges().get(0).getClauseTitle());
        assertEquals("第二部分", response.getChanges().get(0).getParentClauseNo());
        assertEquals("20%", response.getChanges().get(0).getChangedParagraphs().get(0)
                .getChangeDetails().get(0).getOldText());
        assertEquals("30%", response.getChanges().get(0).getChangedParagraphs().get(0)
                .getChangeDetails().get(0).getNewText());
    }

    @Test
    public void shouldExportComparisonAsReadableExcel() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币120万元。"));
        ContractCompareService service = new ContractCompareService(loader,
                new AsposeCompareService(), new ContractStructureParser(),
                new ClauseComparisonEngine(new ContractCompareProperties()));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");

        byte[] excel = service.exportExcel(request);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals("比对结果", workbook.getSheetAt(0).getSheetName());
            assertEquals("条款编号", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("第二条", workbook.getSheetAt(0).getRow(2).getCell(1).getStringCellValue());
            assertEquals("修改", workbook.getSheetAt(0).getRow(2).getCell(4).getStringCellValue());
            assertEquals("本合同总金额为人民币100万元。",
                    workbook.getSheetAt(0).getRow(2).getCell(6).getStringCellValue());
            assertEquals("本合同总金额为人民币120万元。",
                    workbook.getSheetAt(0).getRow(2).getCell(7).getStringCellValue());
            assertEquals("替换：100 → 120",
                    workbook.getSheetAt(0).getRow(2).getCell(8).getStringCellValue());
            assertEquals(14, workbook.getSheetAt(0).getRow(1).getLastCellNum());
            assertEquals("业务变更类型",
                    workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
            assertEquals("识别状态",
                    workbook.getSheetAt(0).getRow(1).getCell(10).getStringCellValue());
        }
    }

    @Test
    public void shouldReturnFullClauseContextAndExportContextColumns() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "前置约定不变。", "本合同总金额为人民币100万元。", "后置约定不变。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "前置约定不变。", "本合同总金额为人民币120万元。", "后置约定不变。"));
        ContractCompareService service = new ContractCompareService(loader,
                new AsposeCompareService(), new ContractStructureParser(),
                new ClauseComparisonEngine(new ContractCompareProperties()));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");
        request.setResultMode(ResultMode.CONTEXT);

        ContractCompareResponse response = service.compare(request);

        assertEquals(1, response.getChanges().get(0).getChangedParagraphs().size());
        assertEquals("第二条 合同金额\n前置约定不变。\n本合同总金额为人民币100万元。\n后置约定不变。",
                response.getChanges().get(0).getContext().getOldContent());
        assertEquals("第二条 合同金额\n前置约定不变。\n本合同总金额为人民币120万元。\n后置约定不变。",
                response.getChanges().get(0).getContext().getNewContent());

        byte[] excel = service.exportExcel(request);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(16, workbook.getSheetAt(0).getRow(1).getLastCellNum());
            assertEquals("完整变更前条款",
                    workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
            assertTrue(workbook.getSheetAt(0).getRow(2).getCell(9).getStringCellValue()
                    .contains("前置约定不变。"));
            assertTrue(workbook.getSheetAt(0).getRow(2).getCell(10).getStringCellValue()
                    .contains("后置约定不变。"));
        }
    }

    @Test
    public void shouldTreatExplicitNullModeAsSimple() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document("第一条 金额", "金额为100万元。"));
        when(loader.load("/new.docx")).thenReturn(document("第一条 金额", "金额为120万元。"));
        ContractCompareService service = new ContractCompareService(loader,
                new AsposeCompareService(), new ContractStructureParser(),
                new ClauseComparisonEngine(new ContractCompareProperties()));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");
        request.setResultMode(null);

        assertNull(service.compare(request).getChanges().get(0).getContext());
    }

    @Test
    public void shouldPredictWithContextBeforeTrimmingSimpleResponse() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "前置约定。", "金额为100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "前置约定。", "金额为120万元。"));
        AsposeCompareService aspose = new AsposeCompareService();
        ClauseComparisonEngine engine =
                new ClauseComparisonEngine(new ContractCompareProperties());
        ContractComparePredictionService prediction =
                mock(ContractComparePredictionService.class);
        doAnswer(invocation -> {
            ContractCompareResponse value = invocation.getArgument(0);
            assertNotNull(value.getChanges().get(0).getContext());
            return null;
        }).when(prediction).predict(any(ContractCompareResponse.class),
                eq(AnalysisType.DOUBLE_VERSION), eq("employee-001"));
        ContractCompareService service = new ContractCompareService(loader, aspose,
                new ContractStructureParser(), engine,
                new ChangeDocumentExtractionService(aspose, engine), prediction);
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");
        request.setResultMode(ResultMode.SIMPLE);

        ContractCompareResponse response = service.compare(request, "employee-001");

        assertNull(response.getChanges().get(0).getContext());
    }

    @Test
    public void shouldExportFailureStatusWithoutFakeSimilarity() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "金额为100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "金额为120万元。"));
        AsposeCompareService aspose = new AsposeCompareService();
        ClauseComparisonEngine engine =
                new ClauseComparisonEngine(new ContractCompareProperties());
        ContractComparePredictionService prediction =
                mock(ContractComparePredictionService.class);
        doAnswer(invocation -> {
            ContractCompareResponse value = invocation.getArgument(0);
            BusinessTypePrediction failed = new BusinessTypePrediction();
            failed.setStatus("FAILED");
            failed.setInputScope("NEW_PARAGRAPH");
            value.getChanges().get(0).getChangedParagraphs().get(0)
                    .setBusinessTypePrediction(failed);
            return null;
        }).when(prediction).predict(any(ContractCompareResponse.class),
                eq(AnalysisType.DOUBLE_VERSION), eq("employee-001"));
        ContractCompareService service = new ContractCompareService(loader, aspose,
                new ContractStructureParser(), engine,
                new ChangeDocumentExtractionService(aspose, engine), prediction);
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");

        byte[] excel = service.exportExcel(request, "employee-001");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals("FAILED",
                    workbook.getSheetAt(0).getRow(2).getCell(10).getStringCellValue());
            assertEquals("",
                    workbook.getSheetAt(0).getRow(2).getCell(12).getStringCellValue());
            assertEquals("NEW_PARAGRAPH",
                    workbook.getSheetAt(0).getRow(2).getCell(13).getStringCellValue());
        }
    }

    @Test
    public void shouldIgnoreContextModeForChangeDocumentAndExportPredictionColumns()
            throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/supplement.docx")).thenReturn(document(
                "1、《基金合同》第二条“合同金额”约定如下：",
                "合同金额为100万元。",
                "上述内容变更如下：",
                "合同金额为120万元。"));
        ContractCompareService service = new ContractCompareService(loader,
                new AsposeCompareService(), new ContractStructureParser(),
                new ClauseComparisonEngine(new ContractCompareProperties()));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setAnalysisType(AnalysisType.CHANGE_DOCUMENT);
        request.setChangeFileGetPath("/supplement.docx");
        request.setResultMode(ResultMode.CONTEXT);

        ContractCompareResponse response = service.compare(request);

        assertEquals(1, response.getTotalChanges());
        assertEquals("1、《基金合同》第二条“合同金额”约定如下：",
                response.getChanges().get(0).getSourceHeading());
        assertNull(response.getChanges().get(0).getContext());

        byte[] excel = service.exportExcel(request);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals("提取结果", workbook.getSheetAt(0).getSheetName());
            assertEquals(13, workbook.getSheetAt(0).getRow(1).getLastCellNum());
            assertEquals("来源标题",
                    workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("合同金额为100万元。",
                    workbook.getSheetAt(0).getRow(2).getCell(5).getStringCellValue());
            assertEquals("合同金额为120万元。",
                    workbook.getSheetAt(0).getRow(2).getCell(6).getStringCellValue());
        }

        request.setResultMode(ResultMode.SIMPLE);
        byte[] simpleExcel = service.exportExcel(request);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(simpleExcel))) {
            assertEquals(13, workbook.getSheetAt(0).getRow(1).getLastCellNum());
        }
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
