package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.common.constants.CommonConstants;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogDO;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogStatus;
import com.citics.glxt.contractchange.contractcompare.model.ContractChangeAnalysisResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.PredictionSummary;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractorRegistry;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** /compare 的文件策略、向量预测、完整留痕和精简响应编排。 */
@Service
public class ContractChangeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(ContractChangeAnalysisService.class);
    private static final int MAX_USER_ID_LENGTH = 100;
    private static final int MAX_FILE_PATH_LENGTH = 1000;

    private final ContractChangeExtractorRegistry extractorRegistry;
    private final ContractComparePredictionService predictionService;
    private final AnalysisResultPersistenceService persistenceService;
    private final ContractChangeProperties properties;
    private final ObjectMapper objectMapper;

    public ContractChangeAnalysisService(ContractChangeExtractorRegistry extractorRegistry,
                                         ContractComparePredictionService predictionService,
                                         AnalysisResultPersistenceService persistenceService,
                                         ContractChangeProperties properties,
                                         ObjectMapper objectMapper) {
        this.extractorRegistry = extractorRegistry;
        this.predictionService = predictionService;
        this.persistenceService = persistenceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ContractChangeAnalysisResponse analyze(ContractCompareRequest request) {
        long totalStart = System.nanoTime();
        requireCompareMetadata(request);
        AnalysisType analysisType = request.getAnalysisType();
        String userId = request.getUserId().trim();
        ContractChangeExtractor extractor = extractorRegistry.get(analysisType);
        extractor.validateRequest(request);
        validateActiveFilePaths(request, analysisType);
        String analysisId = newAnalysisId();
        persistenceService.createProcessing(processingLog(
                analysisId, request, analysisType, userId));

        try {
            long compareStart = System.nanoTime();
            ContractCompareResponse detail = extractor.extract(request);
            long compareCostMs = elapsedMillis(compareStart);
            if (detail == null) {
                throw new ContractChangeBusinessException("合同分析结果不能为空");
            }

            long predictStart = System.nanoTime();
            predictionService.predict(detail, analysisType, userId, analysisId);
            long predictCostMs = elapsedMillis(predictStart);

            AnalysisLogStatus finalStatus = finalStatus(detail);
            String failureReason = finalStatus == AnalysisLogStatus.FAILED
                    ? predictionFailureReason(detail) : null;
            AnalysisLogDO finalLog = finalLog(analysisId, detail, finalStatus,
                    elapsedMillis(totalStart), compareCostMs, predictCostMs);
            finalLog.setErrorMessage(failureReason);
            if (finalStatus == AnalysisLogStatus.FAILED) {
                // 主记录只存完整成功结果的聚合编码；失败时部分编码只保留在诊断快照中。
                finalLog.setChangeTypeCodes(null);
            }
            finalLog.setResultJson(serialize(detail));
            persistenceService.persistSuccess(finalLog, detail, analysisType);

            List<String> codes = finalStatus == AnalysisLogStatus.FAILED
                    || detail.getFileChangeTypeCodes() == null
                    ? Collections.<String>emptyList() : detail.getFileChangeTypeCodes();
            ContractChangeAnalysisResponse result =
                    new ContractChangeAnalysisResponse(analysisId, codes, finalStatus);
            if (finalStatus == AnalysisLogStatus.FAILED) {
                result.setFailureReason(failureReason);
                result.setFailureCode(failureCode(detail));
                log.error("合同分析未完整完成, analysisId={}, analysisType={}, status={}, reason={}, elapsedMs={}",
                        analysisId, analysisType, finalStatus, failureReason,
                        elapsedMillis(totalStart));
            } else {
                log.info("合同分析完成, analysisId={}, analysisType={}, status={}, changedParagraphs={}, elapsedMs={}",
                        analysisId, analysisType, finalStatus, changedParagraphCount(detail),
                        elapsedMillis(totalStart));
            }
            return result;
        } catch (RuntimeException ex) {
            log.error("合同分析异常终止, analysisId={}, analysisType={}, failureType={}, elapsedMs={}",
                    analysisId, analysisType, ex.getClass().getSimpleName(),
                    elapsedMillis(totalStart));
            markFailed(analysisId, elapsedMillis(totalStart), ex);
            throw ex;
        }
    }

    private AnalysisLogDO processingLog(String analysisId, ContractCompareRequest request,
                                        AnalysisType analysisType, String userId) {
        AnalysisLogDO log = new AnalysisLogDO();
        log.setAnalysisId(analysisId);
        log.setInstId(request.getInstId());
        log.setUserId(userId);
        log.setAnalysisType(analysisType.name());
        if (analysisType == AnalysisType.DOUBLE_VERSION) {
            log.setOldFileGetPath(request.getOldFileGetPath());
            log.setNewFileGetPath(request.getNewFileGetPath());
        } else {
            log.setChangeFileGetPath(request.getChangeFileGetPath());
        }
        log.setStatus(AnalysisLogStatus.PROCESSING.name());
        log.setEmbeddingModel(properties.getEmbedding().getModelName());
        log.setModelVersion(properties.getEmbedding().getModelVersion());
        log.setCreateTime(new Date());
        return log;
    }

    private AnalysisLogDO finalLog(String analysisId, ContractCompareResponse detail,
                                   AnalysisLogStatus status, long totalCostMs,
                                   long compareCostMs, long predictCostMs) {
        AnalysisLogDO log = new AnalysisLogDO();
        log.setAnalysisId(analysisId);
        log.setStatus(status.name());
        log.setChangedParagraphCount(changedParagraphCount(detail));
        log.setChangeTypeCodes(joinCodes(detail.getFileChangeTypeCodes()));
        log.setTotalCostMs(totalCostMs);
        log.setCompareCostMs(compareCostMs);
        log.setPredictCostMs(predictCostMs);
        log.setFinishTime(new Date());
        return log;
    }

    private AnalysisLogStatus finalStatus(ContractCompareResponse detail) {
        PredictionSummary summary = detail.getPredictionSummary();
        if (summary != null && (summary.getFailedCount() > 0
                || summary.getSkippedTooLongCount() > 0)) {
            return AnalysisLogStatus.FAILED;
        }
        if (detail.getChanges() != null) {
            for (ClauseChange change : detail.getChanges()) {
                if (change == null || change.getChangedParagraphs() == null) continue;
                for (ContractCompareResponse.ChangedParagraph paragraph : change.getChangedParagraphs()) {
                    if (paragraph == null || paragraph.getBusinessTypePrediction() == null) continue;
                    String status = paragraph.getBusinessTypePrediction().getStatus();
                    if ("FAILED".equals(status) || "SKIPPED_TOO_LONG".equals(status)) {
                        return AnalysisLogStatus.FAILED;
                    }
                }
            }
        }
        return AnalysisLogStatus.SUCCESS;
    }

    private String predictionFailureReason(ContractCompareResponse detail) {
        PredictionSummary summary = detail.getPredictionSummary();
        int failed = summary == null ? countPredictionStatus(detail, "FAILED")
                : summary.getFailedCount();
        int skipped = summary == null ? countPredictionStatus(detail, "SKIPPED_TOO_LONG")
                : summary.getSkippedTooLongCount();
        StringBuilder reason = new StringBuilder("合同比对失败：业务类型识别未全部完成");
        if (failed > 0) reason.append("，调用或识别失败 ").append(failed).append(" 个段落");
        if (skipped > 0) reason.append("，超长未识别 ").append(skipped).append(" 个段落");
        String first = firstPredictionFailure(detail);
        if (StringUtils.hasText(first)) reason.append("；示例原因：").append(first);
        return reason.toString();
    }

    private int countPredictionStatus(ContractCompareResponse detail, String expectedStatus) {
        int count = 0;
        if (detail.getChanges() == null) return count;
        for (ClauseChange change : detail.getChanges()) {
            if (change == null || change.getChangedParagraphs() == null) continue;
            for (ContractCompareResponse.ChangedParagraph paragraph : change.getChangedParagraphs()) {
                if (paragraph != null && paragraph.getBusinessTypePrediction() != null
                        && expectedStatus.equals(paragraph.getBusinessTypePrediction().getStatus())) {
                    count++;
                }
            }
        }
        return count;
    }

    private String firstPredictionFailure(ContractCompareResponse detail) {
        if (detail.getChanges() == null) return null;
        for (ClauseChange change : detail.getChanges()) {
            if (change == null || change.getChangedParagraphs() == null) continue;
            for (ContractCompareResponse.ChangedParagraph paragraph : change.getChangedParagraphs()) {
                if (paragraph == null || paragraph.getBusinessTypePrediction() == null) continue;
                String status = paragraph.getBusinessTypePrediction().getStatus();
                if ("FAILED".equals(status) || "SKIPPED_TOO_LONG".equals(status)) {
                    return paragraph.getBusinessTypePrediction().getMessage();
                }
            }
        }
        return "请查看分析记录中的段落级识别状态";
    }

    private int failureCode(ContractCompareResponse detail) {
        boolean invalidInput = false;
        if (detail.getChanges() == null) return CommonConstants.FAIL;
        for (ClauseChange change : detail.getChanges()) {
            if (change == null || change.getChangedParagraphs() == null) continue;
            for (ContractCompareResponse.ChangedParagraph paragraph : change.getChangedParagraphs()) {
                if (paragraph == null || paragraph.getBusinessTypePrediction() == null) continue;
                String errorCode = paragraph.getBusinessTypePrediction().getErrorCode();
                if ("INDEX_UNAVAILABLE".equals(errorCode)
                        || "EMBEDDING_UNAVAILABLE".equals(errorCode)) {
                    return CommonConstants.SERVICE_UNAVAILABLE;
                }
                if ("INVALID_INPUT".equals(errorCode) || "EMPTY_INPUT".equals(errorCode)
                        || "INPUT_TOO_LONG".equals(errorCode)) invalidInput = true;
            }
        }
        return invalidInput ? CommonConstants.BAD_REQUEST : CommonConstants.FAIL;
    }

    private int changedParagraphCount(ContractCompareResponse detail) {
        int count = 0;
        if (detail.getChanges() != null) {
            for (ClauseChange change : detail.getChanges()) {
                if (change != null && change.getChangedParagraphs() != null) {
                    count += change.getChangedParagraphs().size();
                }
            }
        }
        return count;
    }

    private String serialize(ContractCompareResponse detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new ContractChangeBusinessException("合同分析完整结果序列化失败");
        }
    }

    private void markFailed(String analysisId, long totalCostMs, RuntimeException failure) {
        try {
            persistenceService.markFailed(analysisId, totalCostMs, errorMessage(failure));
            log.error("合同分析失败状态已保存, analysisId={}, failureType={}, elapsedMs={}",
                    analysisId, failure.getClass().getSimpleName(), totalCostMs);
        } catch (RuntimeException logFailure) {
            failure.addSuppressed(logFailure);
            log.error("合同分析失败状态保存失败, analysisId={}", analysisId, logFailure);
        }
    }

    private void requireCompareMetadata(ContractCompareRequest request) {
        if (request == null) {
            throw new ContractChangeBusinessException("合同比较请求不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new ContractChangeBusinessException("userId不能为空");
        }
        if (request.getUserId().trim().length() > MAX_USER_ID_LENGTH) {
            throw new ContractChangeBusinessException("userId不能超过100个字符");
        }
        if (request.getInstId() == null) {
            throw new ContractChangeBusinessException("instId不能为空");
        }
        if (request.getInstId() <= 0) {
            throw new ContractChangeBusinessException("instId必须大于0");
        }
        if (request.getAnalysisType() == null) {
            throw new ContractChangeBusinessException("analysisType不能为空");
        }
    }

    private void validateActiveFilePaths(ContractCompareRequest request, AnalysisType analysisType) {
        if (analysisType == AnalysisType.DOUBLE_VERSION) {
            requirePathLength(request.getOldFileGetPath(), "oldFileGetPath");
            requirePathLength(request.getNewFileGetPath(), "newFileGetPath");
        } else if (analysisType == AnalysisType.CHANGE_DOCUMENT) {
            requirePathLength(request.getChangeFileGetPath(), "changeFileGetPath");
        }
    }

    private void requirePathLength(String path, String fieldName) {
        if (path != null && path.length() > MAX_FILE_PATH_LENGTH) {
            throw new ContractChangeBusinessException(fieldName + "不能超过1000个字符");
        }
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String code : codes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(code.trim());
        }
        return result.length() == 0 ? null : result.toString();
    }

    private String errorMessage(RuntimeException failure) {
        return StringUtils.hasText(failure.getMessage())
                ? failure.getClass().getName() + ": " + failure.getMessage()
                : failure.getClass().getName();
    }

    private long elapsedMillis(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    private String newAnalysisId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
