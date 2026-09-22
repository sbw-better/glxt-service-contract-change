package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
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
        requireCompareMetadata(request);
        AnalysisType analysisType = request.getAnalysisType();
        String userId = request.getUserId().trim();
        ContractChangeExtractor extractor = extractorRegistry.get(analysisType);
        extractor.validateRequest(request);
        String analysisId = newAnalysisId();
        persistenceService.createProcessing(processingLog(
                analysisId, request, analysisType, userId));

        long totalStart = System.nanoTime();
        try {
            long compareStart = System.nanoTime();
            ContractCompareResponse detail = extractor.extract(request);
            long compareCostMs = elapsedMillis(compareStart);
            if (detail == null) {
                throw new ContractChangeBusinessException("合同分析结果不能为空");
            }

            long predictStart = System.nanoTime();
            predictionService.predict(detail, analysisType, userId);
            long predictCostMs = elapsedMillis(predictStart);

            AnalysisLogStatus finalStatus = finalStatus(detail);
            AnalysisLogDO finalLog = finalLog(analysisId, detail, finalStatus,
                    elapsedMillis(totalStart), compareCostMs, predictCostMs);
            finalLog.setResultJson(serialize(detail));
            persistenceService.persistSuccess(finalLog, detail, analysisType);

            List<String> codes = detail.getFileChangeTypeCodes() == null
                    ? Collections.<String>emptyList() : detail.getFileChangeTypeCodes();
            return new ContractChangeAnalysisResponse(analysisId, codes, finalStatus);
        } catch (RuntimeException ex) {
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
        log.setOldFileGetPath(request.getOldFileGetPath());
        log.setNewFileGetPath(request.getNewFileGetPath());
        log.setChangeFileGetPath(request.getChangeFileGetPath());
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
            return AnalysisLogStatus.PARTIAL_SUCCESS;
        }
        return AnalysisLogStatus.SUCCESS;
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
        if (request.getInstId() == null) {
            throw new ContractChangeBusinessException("instId不能为空");
        }
        if (request.getAnalysisType() == null) {
            throw new ContractChangeBusinessException("analysisType不能为空");
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
