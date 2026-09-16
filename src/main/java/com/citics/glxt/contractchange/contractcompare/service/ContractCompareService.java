package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.common.constants.CommonConstants;
import com.citics.glxt.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService.ComparisonResult;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.ResultMode;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeDetail;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.DetailType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.BusinessTypePrediction;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.contractcompare.service.ClauseComparisonEngine.Analysis;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 合同双版本比较应用服务。 */
@Service
public class ContractCompareService {
    private static final int EXCEL_CELL_TEXT_LIMIT = 32767;
    private static final String[] SIMPLE_EXPORT_HEADERS = new String[]{
            "序号", "条款编号", "条款标题", "上级条款编号", "条款变更类型",
            "段落变更类型", "变更前内容", "变更后内容", "具体变化",
            "业务变更类型", "识别状态", "预测等级", "最高相似度", "识别输入范围"
    };
    private static final String[] CONTEXT_EXPORT_HEADERS = new String[]{
            "序号", "条款编号", "条款标题", "上级条款编号", "条款变更类型",
            "段落变更类型", "变更前内容", "变更后内容", "具体变化",
            "完整变更前条款", "完整变更后条款", "业务变更类型", "识别状态",
            "预测等级", "最高相似度", "识别输入范围"
    };
    private static final String[] CHANGE_DOCUMENT_SIMPLE_HEADERS = new String[]{
            "序号", "来源标题", "目标条款", "条款变更类型",
            "段落变更类型", "变更前内容", "变更后内容", "具体变化",
            "业务变更类型", "识别状态", "预测等级", "最高相似度", "识别输入范围"
    };
    private final SftpContractFileLoader fileLoader;
    private final AsposeCompareService asposeCompareService;
    private final ContractStructureParser structureParser;
    private final ClauseComparisonEngine comparisonEngine;
    private final ChangeDocumentExtractionService extractionService;
    private final ContractComparePredictionService predictionService;

    @Autowired
    public ContractCompareService(SftpContractFileLoader fileLoader,
                                  AsposeCompareService asposeCompareService,
                                  ContractStructureParser structureParser,
                                  ClauseComparisonEngine comparisonEngine,
                                  ChangeDocumentExtractionService extractionService,
                                  ContractComparePredictionService predictionService) {
        this.fileLoader = fileLoader;
        this.asposeCompareService = asposeCompareService;
        this.structureParser = structureParser;
        this.comparisonEngine = comparisonEngine;
        this.extractionService = extractionService;
        this.predictionService = predictionService;
    }

    /** 保留核心链路单元测试和直接调用的兼容构造方式。 */
    public ContractCompareService(SftpContractFileLoader fileLoader,
                                  AsposeCompareService asposeCompareService,
                                  ContractStructureParser structureParser,
                                  ClauseComparisonEngine comparisonEngine) {
        this(fileLoader, asposeCompareService, structureParser, comparisonEngine,
                new ChangeDocumentExtractionService(asposeCompareService, comparisonEngine),
                null);
    }

    /** 保留已有单文件提取测试使用的构造方式。 */
    public ContractCompareService(SftpContractFileLoader fileLoader,
                                  AsposeCompareService asposeCompareService,
                                  ContractStructureParser structureParser,
                                  ClauseComparisonEngine comparisonEngine,
                                  ChangeDocumentExtractionService extractionService) {
        this(fileLoader, asposeCompareService, structureParser, comparisonEngine,
                extractionService, null);
    }

    public ContractCompareResponse compare(ContractCompareRequest request) {
        ContractCompareResponse response = analyze(request);
        trimContext(response, resultMode(request));
        return response;
    }

    /** 生产接口使用：完成比对和业务类型识别后返回。 */
    public ContractCompareResponse compare(ContractCompareRequest request, String userId) {
        requireIntegratedServices();
        ContractCompareResponse response = analyze(request);
        predictionService.predict(response, analysisType(request), userId);
        trimContext(response, resultMode(request));
        return response;
    }

    private ContractCompareResponse analyze(ContractCompareRequest request) {
        if (analysisType(request) == AnalysisType.CHANGE_DOCUMENT) {
            byte[] bytes = fileLoader.load(request.getChangeFileGetPath());
            return extractionService.extract(bytes);
        }
        byte[] oldBytes = fileLoader.load(request.getOldFileGetPath());
        byte[] newBytes = fileLoader.load(request.getNewFileGetPath());
        ComparisonResult compared = asposeCompareService.compare(oldBytes, newBytes);
        try {
            Parsed oldContract = structureParser.parse(compared.getOldDocument());
            Parsed newContract = structureParser.parse(compared.getNewDocument());
            Analysis analysis = comparisonEngine.analyze(oldContract, newContract, compared.getRevisions());
            return new ContractCompareResponse(analysis.getChanges().size(),
                    analysis.getChanges(), analysis.getWarnings());
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new com.citics.glxt.common.exception.ContractChangeBusinessException("合同结构解析失败");
        }
    }

    /** 比较两份合同并生成供业务核对的Excel。 */
    public byte[] exportExcel(ContractCompareRequest request) {
        ResultMode mode = resultMode(request);
        ContractCompareResponse response = compare(request);
        return exportExcel(response, analysisType(request), mode);
    }

    /** 执行一次完整分析和业务类型识别并导出。 */
    public byte[] exportExcel(ContractCompareRequest request, String userId) {
        ContractCompareResponse response = compare(request, userId);
        return exportExcel(response, analysisType(request), resultMode(request));
    }

    private byte[] exportExcel(ContractCompareResponse response, AnalysisType type, ResultMode mode) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (type == AnalysisType.CHANGE_DOCUMENT) {
                writeChangeDocumentSheet(workbook, response);
            } else {
                writeResultSheet(workbook, response, mode);
            }
            writeWarningSheet(workbook, response.getWarnings());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ContractChangeBusinessException(CommonConstants.FAIL, "合同比对结果Excel生成失败");
        }
    }

    private ResultMode resultMode(ContractCompareRequest request) {
        return request.getResultMode() == null ? ResultMode.SIMPLE : request.getResultMode();
    }

    private AnalysisType analysisType(ContractCompareRequest request) {
        return request.getAnalysisType() == null
                ? AnalysisType.DOUBLE_VERSION : request.getAnalysisType();
    }

    private void writeChangeDocumentSheet(XSSFWorkbook workbook,
                                          ContractCompareResponse response) {
        String[] headers = CHANGE_DOCUMENT_SIMPLE_HEADERS;
        Sheet sheet = workbook.createSheet("提取结果");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle contentStyle = contentStyle(workbook);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("变更函条款提取结果（条款数：" + response.getTotalChanges() + "）");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        Row header = sheet.createRow(1);
        header.setHeightInPoints(24);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(headerStyle);
        }

        int rowNumber = 2;
        int sequence = 1;
        for (ClauseChange change : response.getChanges()) {
            List<ChangedParagraph> paragraphs = change.getChangedParagraphs();
            if (paragraphs == null || paragraphs.isEmpty()) {
                rowNumber = writeChangeDocumentRow(sheet, rowNumber, sequence++,
                        change, null, contentStyle);
                continue;
            }
            for (ChangedParagraph paragraph : paragraphs) {
                rowNumber = writeChangeDocumentRow(sheet, rowNumber, sequence++,
                        change, paragraph, contentStyle);
            }
        }

        sheet.createFreezePane(0, 2);
        sheet.setAutoFilter(new CellRangeAddress(1, Math.max(1, rowNumber - 1),
                0, headers.length - 1));
        int[] widths = new int[]{8, 55, 45, 14, 14, 60, 60, 60, 32, 20, 18, 16, 20};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }

    private int writeChangeDocumentRow(Sheet sheet, int rowNumber, int sequence,
                                       ClauseChange change, ChangedParagraph paragraph,
                                       CellStyle style) {
        Row row = sheet.createRow(rowNumber);
        row.setHeightInPoints(48);
        setCell(row, 0, String.valueOf(sequence), style);
        setCell(row, 1, change.getSourceHeading(), style);
        setCell(row, 2, change.getTargetClauseReference(), style);
        setCell(row, 3, changeTypeText(change.getChangeType()), style);
        setCell(row, 4, paragraph == null ? null
                : changeTypeText(paragraph.getParagraphChangeType()), style);
        setCell(row, 5, paragraph == null ? null : paragraph.getOldContent(), style);
        setCell(row, 6, paragraph == null ? null : paragraph.getNewContent(), style);
        setCell(row, 7, paragraph == null ? null
                : detailText(paragraph.getChangeDetails()), style);
        writePredictionCells(row, 8, paragraph, style);
        return rowNumber + 1;
    }

    private void writeResultSheet(XSSFWorkbook workbook, ContractCompareResponse response,
                                  ResultMode mode) {
        boolean includeContext = mode == ResultMode.CONTEXT;
        String[] headers = includeContext ? CONTEXT_EXPORT_HEADERS : SIMPLE_EXPORT_HEADERS;
        Sheet sheet = workbook.createSheet("比对结果");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle contentStyle = contentStyle(workbook);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("合同双版本比对结果（条款变更数：" + response.getTotalChanges() + "）");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        Row header = sheet.createRow(1);
        header.setHeightInPoints(24);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(headerStyle);
        }

        int rowNumber = 2;
        int sequence = 1;
        for (ClauseChange change : response.getChanges()) {
            List<ChangedParagraph> paragraphs = change.getChangedParagraphs();
            if (paragraphs == null || paragraphs.isEmpty()) {
                rowNumber = writeResultRow(sheet, rowNumber, sequence++, change, null,
                        contentStyle, includeContext);
                continue;
            }
            for (ChangedParagraph paragraph : paragraphs) {
                rowNumber = writeResultRow(sheet, rowNumber, sequence++, change, paragraph,
                        contentStyle, includeContext);
            }
        }

        sheet.createFreezePane(0, 2);
        sheet.setAutoFilter(new CellRangeAddress(1, Math.max(1, rowNumber - 1),
                0, headers.length - 1));
        int[] widths = includeContext
                ? new int[]{8, 16, 24, 18, 14, 14, 60, 60, 60, 80, 80, 32, 20, 18, 16, 20}
                : new int[]{8, 16, 24, 18, 14, 14, 60, 60, 60, 32, 20, 18, 16, 20};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }

    private int writeResultRow(Sheet sheet, int rowNumber, int sequence,
                               ClauseChange change, ChangedParagraph paragraph,
                               CellStyle style, boolean includeContext) {
        Row row = sheet.createRow(rowNumber);
        row.setHeightInPoints(48);
        setCell(row, 0, String.valueOf(sequence), style);
        setCell(row, 1, change.getClauseNo(), style);
        setCell(row, 2, change.getClauseTitle(), style);
        setCell(row, 3, change.getParentClauseNo(), style);
        setCell(row, 4, changeTypeText(change.getChangeType()), style);
        setCell(row, 5, paragraph == null ? null
                : changeTypeText(paragraph.getParagraphChangeType()), style);
        setCell(row, 6, paragraph == null ? null : paragraph.getOldContent(), style);
        setCell(row, 7, paragraph == null ? null : paragraph.getNewContent(), style);
        setCell(row, 8, paragraph == null ? null
                : detailText(paragraph.getChangeDetails()), style);
        if (includeContext) {
            setCell(row, 9, change.getContext() == null ? null
                    : change.getContext().getOldContent(), style);
            setCell(row, 10, change.getContext() == null ? null
                    : change.getContext().getNewContent(), style);
        }
        writePredictionCells(row, includeContext ? 11 : 9, paragraph, style);
        return rowNumber + 1;
    }

    private void writePredictionCells(Row row, int start, ChangedParagraph paragraph,
                                      CellStyle style) {
        BusinessTypePrediction prediction = paragraph == null ? null
                : paragraph.getBusinessTypePrediction();
        setCell(row, start, predictedTypes(prediction), style);
        setCell(row, start + 1, prediction == null ? null : prediction.getStatus(), style);
        setCell(row, start + 2, predictionLevels(prediction), style);
        boolean similarityAvailable = prediction != null
                && ("MATCHED".equals(prediction.getStatus())
                || "NO_RELIABLE_MATCH".equals(prediction.getStatus()));
        setCell(row, start + 3, similarityAvailable
                ? String.format(java.util.Locale.ROOT, "%.4f", prediction.getMaxSimilarity())
                : null, style);
        setCell(row, start + 4, prediction == null ? null : prediction.getInputScope(), style);
    }

    private String predictedTypes(BusinessTypePrediction prediction) {
        if (prediction == null || prediction.getChangeTypes() == null) {
            return "";
        }
        List<String> codes = new java.util.ArrayList<String>();
        for (ChangeTypePrediction type : prediction.getChangeTypes()) {
            codes.add(type.getCode());
        }
        return join(codes);
    }

    private String predictionLevels(BusinessTypePrediction prediction) {
        if (prediction == null || prediction.getChangeTypes() == null) {
            return "";
        }
        List<String> values = new java.util.ArrayList<String>();
        for (ChangeTypePrediction type : prediction.getChangeTypes()) {
            values.add(type.getCode() + ":" + type.getLevel());
        }
        return join(values);
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(value);
        }
        return result.toString();
    }

    private void trimContext(ContractCompareResponse response, ResultMode mode) {
        if (mode == ResultMode.SIMPLE && response.getChanges() != null) {
            for (ClauseChange change : response.getChanges()) {
                change.setContext(null);
            }
        }
    }

    private void requireIntegratedServices() {
        if (predictionService == null) {
            throw new IllegalStateException("合同比对类型识别服务未配置");
        }
    }

    private void writeWarningSheet(XSSFWorkbook workbook, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        Sheet sheet = workbook.createSheet("提示信息");
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle contentStyle = contentStyle(workbook);
        Row header = sheet.createRow(0);
        setCell(header, 0, "序号", headerStyle);
        setCell(header, 1, "提示", headerStyle);
        for (int index = 0; index < warnings.size(); index++) {
            Row row = sheet.createRow(index + 1);
            setCell(row, 0, String.valueOf(index + 1), contentStyle);
            setCell(row, 1, warnings.get(index), contentStyle);
        }
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 80 * 256);
    }

    private String detailText(List<ChangeDetail> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ChangeDetail detail : details) {
            if (text.length() > 0) {
                text.append('\n');
            }
            if (detail.getDetailType() == DetailType.INSERTED) {
                text.append("新增：").append(value(detail.getNewText()));
            } else if (detail.getDetailType() == DetailType.DELETED) {
                text.append("删除：").append(value(detail.getOldText()));
            } else {
                text.append("替换：").append(value(detail.getOldText()))
                        .append(" → ").append(value(detail.getNewText()));
            }
        }
        return text.toString();
    }

    private String changeTypeText(ChangeType type) {
        if (type == ChangeType.ADDED) {
            return "新增";
        }
        if (type == ChangeType.DELETED) {
            return "删除";
        }
        return type == ChangeType.MODIFIED ? "修改" : "";
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private void setCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(safeCellText(value));
        cell.setCellStyle(style);
    }

    private String safeCellText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\n' || current == '\r' || current == '\t'
                    || current >= 0x20) {
                cleaned.append(current);
            }
        }
        if (cleaned.length() <= EXCEL_CELL_TEXT_LIMIT) {
            return cleaned.toString();
        }
        String suffix = "…（内容过长已截断）";
        return cleaned.substring(0, EXCEL_CELL_TEXT_LIMIT - suffix.length()) + suffix;
    }

    private CellStyle titleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle contentStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        applyBorders(style);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
