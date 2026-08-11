package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.model.ImportErrorItem;
import com.citics.glxt.contractchange.model.ImportResponse;
import com.citics.glxt.contractchange.util.ChangeTypeCodes;
import com.citics.glxt.contractchange.util.ContractTextNormalizer;
import com.citics.glxt.contractchange.util.HashUtils;
import com.citics.glxt.contractchange.util.VectorCodec;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史合同段落 Excel 导入服务。
 *
 * <p>本类负责文件校验、数据规范化、重复/冲突检查和向量生成；真正的批量入库由
 * {@link ContractParagraphPersistenceService} 在独立事务中完成。日志不得输出合同正文、
 * 类型明细或向量内容，仅记录数量、耗时、文本 Hash 和处理状态。</p>
 */
@Slf4j
@Service
public class ContractParagraphImportService {
    private static final String HEADER_PARAGRAPH = "合同段落";
    private static final String HEADER_CODES = "变更类型编码";

    private final ContractParagraphMapper mapper;
    private final ContractParagraphPersistenceService persistenceService;
    private final ParagraphVectorIndexService indexService;
    private final EmbeddingClient embeddingClient;
    private final ContractChangeProperties properties;

    /** 注入导入流程所需的数据访问、事务、索引和模型组件。 */
    public ContractParagraphImportService(ContractParagraphMapper mapper,
                                          ContractParagraphPersistenceService persistenceService,
                                          ParagraphVectorIndexService indexService,
                                          EmbeddingClient embeddingClient,
                                          ContractChangeProperties properties) {
        this.mapper = mapper;
        this.persistenceService = persistenceService;
        this.indexService = indexService;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    /**
     * 同步导入一份历史样本 Excel。
     *
     * <p>模型调用全部成功后才开启数据库事务，避免模型中途失败造成部分数据落库。</p>
     *
     * @param file 固定两列表头的 xlsx 文件
     * @return 导入统计和逐行校验错误
     */
    public ImportResponse importExcel(MultipartFile file) {
        long started = System.currentTimeMillis();
        log.info("历史样本导入开始, fileSizeBytes={}", file == null ? 0L : file.getSize());
        validateFile(file);
        ParsedExcel parsed = parse(file);
        if (!parsed.errors.isEmpty()) {
            log.warn("历史样本导入校验失败, totalRows={}, skipped={}, errorCount={}, elapsedMs={}",
                    parsed.totalRows, parsed.skipped, parsed.errors.size(), System.currentTimeMillis() - started);
            return failed(parsed);
        }

        List<PreparedRow> newRows = new ArrayList<PreparedRow>();
        int skipped = parsed.skipped;
        for (PreparedRow row : parsed.rows.values()) {
            ContractParagraphDO existing = mapper.selectByTextHash(row.textHash);
            if (existing == null) {
                newRows.add(row);
            } else if (existing.getChangeTypeCodes().equals(row.changeTypeCodes)) {
                skipped++;
            } else {
                // 只记录 Hash，不在日志中暴露合同正文。
                log.warn("历史样本导入发现数据库标签冲突, row={}, textHash={}",
                        row.rowNumber, row.textHash);
                parsed.errors.add(new ImportErrorItem(row.rowNumber,
                        "数据库已存在相同段落，但变更类型编码不同"));
            }
        }
        if (!parsed.errors.isEmpty()) {
            log.warn("历史样本导入冲突终止, totalRows={}, skipped={}, conflictCount={}, elapsedMs={}",
                    parsed.totalRows, skipped, parsed.errors.size(), System.currentTimeMillis() - started);
            return new ImportResponse(false, parsed.totalRows, 0, skipped, false, parsed.errors);
        }

        log.info("历史样本导入准备向量化, newCount={}, skipped={}", newRows.size(), skipped);
        embed(newRows);
        List<ContractParagraphDO> paragraphs = new ArrayList<ContractParagraphDO>(newRows.size());
        for (PreparedRow row : newRows) paragraphs.add(toDO(row, file.getOriginalFilename()));
        persistenceService.insertAll(paragraphs);

        boolean indexReloaded = true;
        try {
            indexService.reload();
        } catch (RuntimeException ex) {
            indexReloaded = false;
            log.error("历史样本已入库，但内存索引刷新失败", ex);
        }
        log.info("历史样本导入完成, totalRows={}, inserted={}, skipped={}, indexReloaded={}, elapsedMs={}",
                parsed.totalRows, paragraphs.size(), skipped, indexReloaded,
                System.currentTimeMillis() - started);
        return new ImportResponse(true, parsed.totalRows, paragraphs.size(), skipped,
                indexReloaded, Collections.<ImportErrorItem>emptyList());
    }

    /**
     * 解析并校验 Excel，同时完成段落规范化、Hash 计算和文件内冲突检查。
     * 此阶段不调用模型、不写数据库。
     */
    private ParsedExcel parse(MultipartFile file) {
        ParsedExcel result = new ParsedExcel();
        DataFormatter formatter = new DataFormatter();
        try (InputStream input = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ContractChangeBusinessException("Excel中没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null || !HEADER_PARAGRAPH.equals(value(header.getCell(0), formatter))
                    || !HEADER_CODES.equals(value(header.getCell(1), formatter))) {
                result.errors.add(new ImportErrorItem(1, "表头必须为：合同段落、变更类型编码"));
                return result;
            }
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                String paragraph = row == null ? "" : value(row.getCell(0), formatter);
                String rawCodes = row == null ? "" : value(row.getCell(1), formatter);
                if (paragraph.trim().isEmpty() && rawCodes.trim().isEmpty()) continue;
                result.totalRows++;
                int excelRow = index + 1;
                if (result.totalRows > properties.getImportConfig().getMaxRows()) {
                    result.errors.add(new ImportErrorItem(excelRow,
                            "单次导入不能超过" + properties.getImportConfig().getMaxRows() + "条"));
                    break;
                }
                try {
                    String normalized = ContractTextNormalizer.normalize(paragraph);
                    validateParagraph(normalized);
                    String codes = ChangeTypeCodes.canonicalize(rawCodes);
                    String hash = HashUtils.sha256(normalized);
                    PreparedRow previous = result.rows.get(hash);
                    if (previous == null) {
                        result.rows.put(hash,
                                new PreparedRow(excelRow, paragraph.trim(), normalized, hash, codes));
                    } else if (previous.changeTypeCodes.equals(codes)) {
                        result.skipped++;
                    } else {
                        log.warn("Excel内存在标签冲突, row={}, firstRow={}, textHash={}",
                                excelRow, previous.rowNumber, hash);
                        result.errors.add(new ImportErrorItem(excelRow,
                                "Excel中存在相同段落但变更类型编码不同，首次出现在第" + previous.rowNumber + "行"));
                    }
                } catch (ContractChangeBusinessException ex) {
                    result.errors.add(new ImportErrorItem(excelRow, ex.getMessage()));
                }
            }
        } catch (IOException | POIXMLException ex) {
            log.warn("Excel读取失败, fileSizeBytes={}, exception={}",
                    file.getSize(), ex.getClass().getSimpleName());
            throw new ContractChangeBusinessException("Excel读取失败，请确认文件未损坏且格式为xlsx");
        }
        return result;
    }

    /** 按配置的批量大小生成全部待新增样本向量，任一批次失败即终止导入。 */
    private void embed(List<PreparedRow> rows) {
        int batchSize = Math.max(1, properties.getEmbedding().getBatchSize());
        for (int start = 0; start < rows.size(); start += batchSize) {
            int end = Math.min(start + batchSize, rows.size());
            log.debug("历史样本分批向量化, batchStart={}, batchEnd={}, total={}", start, end, rows.size());
            List<String> texts = new ArrayList<String>(end - start);
            for (int i = start; i < end; i++) texts.add(rows.get(i).normalizedText);
            EmbeddingBatchResult result = embeddingClient.embed(texts);
            if (result.getDimension() != properties.getEmbedding().getDimension()
                    || result.getVectors().size() != texts.size()) {
                throw new ContractChangeBusinessException("Embedding批量结果不完整");
            }
            for (int i = start; i < end; i++) rows.get(i).vector = result.getVectors().get(i - start);
        }
    }

    /** 将校验完成的临时行转换为可持久化对象，并把 Float32 向量编码为 BLOB。 */
    private ContractParagraphDO toDO(PreparedRow row, String sourceFile) {
        ContractParagraphDO value = new ContractParagraphDO();
        value.setOriginalText(row.originalText);
        value.setNormalizedText(row.normalizedText);
        value.setTextHash(row.textHash);
        value.setChangeTypeCodes(row.changeTypeCodes);
        value.setVectorData(VectorCodec.encode(row.vector));
        value.setVectorDim(properties.getEmbedding().getDimension());
        value.setModelVersion(properties.getEmbedding().getModelVersion());
        value.setSourceFile(sourceFile != null && sourceFile.length() > 500
                ? sourceFile.substring(0, 500) : sourceFile);
        value.setEnabled(1);
        return value;
    }

    /** 校验上传文件存在且扩展名为 xlsx；文件大小由 Spring Multipart 配置统一限制。 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ContractChangeBusinessException("Excel文件不能为空");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw new ContractChangeBusinessException("只支持xlsx格式文件");
        }
    }

    /** 校验规范化后的段落长度，确保导入与预测使用同一业务上限。 */
    private void validateParagraph(String paragraph) {
        if (paragraph.isEmpty()) throw new ContractChangeBusinessException("合同段落不能为空");
        if (paragraph.length() > properties.getSearch().getMaxParagraphLength()) {
            throw new ContractChangeBusinessException(
                    "合同段落不能超过" + properties.getSearch().getMaxParagraphLength() + "字符");
        }
    }

    /** 以 Excel 显示值读取单元格，兼容字符串、数字和公式结果。 */
    private String value(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    /** 构造未发生数据库写入的导入失败响应。 */
    private ImportResponse failed(ParsedExcel parsed) {
        return new ImportResponse(false, parsed.totalRows, 0, parsed.skipped, false, parsed.errors);
    }

    private static class ParsedExcel {
        /** Excel 非空数据行数量。 */
        private int totalRows;
        /** Excel 内部标签相同的重复行数量。 */
        private int skipped;
        /** 按文本 Hash 去重并保持 Excel 原始顺序的有效行。 */
        private final Map<String, PreparedRow> rows = new LinkedHashMap<String, PreparedRow>();
        /** 可一次性返回给操作人员的逐行错误。 */
        private final List<ImportErrorItem> errors = new ArrayList<ImportErrorItem>();
    }

    private static class PreparedRow {
        /** 原 Excel 行号，用于向调用方返回准确错误位置。 */
        private final int rowNumber;
        /** 用于数据库追溯和预测证据展示的原始段落。 */
        private final String originalText;
        /** 用于 Hash 和模型计算的规范化段落。 */
        private final String normalizedText;
        /** 规范化段落的 SHA-256。 */
        private final String textHash;
        /** 已去重、排序的类型编码字符串。 */
        private final String changeTypeCodes;
        /** 模型调用完成后回填的归一化向量。 */
        private float[] vector;

        private PreparedRow(int rowNumber, String originalText, String normalizedText,
                            String textHash, String changeTypeCodes) {
            this.rowNumber = rowNumber;
            this.originalText = originalText;
            this.normalizedText = normalizedText;
            this.textHash = textHash;
            this.changeTypeCodes = changeTypeCodes;
        }
    }
}
