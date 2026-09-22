package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogDO;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogStatus;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisMatchLogDO;
import com.citics.glxt.contractchange.contractcompare.model.AnalysisParagraphLogDO;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.BusinessTypePrediction;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.mapper.AnalysisLogMapper;
import com.citics.glxt.contractchange.mapper.AnalysisMatchLogMapper;
import com.citics.glxt.contractchange.mapper.AnalysisParagraphLogMapper;
import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 在短事务中原子保存 /compare 主记录、段落结果和匹配证据。 */
@Service
public class AnalysisResultPersistenceService {
    private final AnalysisLogMapper analysisLogMapper;
    private final AnalysisParagraphLogMapper paragraphLogMapper;
    private final AnalysisMatchLogMapper matchLogMapper;

    public AnalysisResultPersistenceService(AnalysisLogMapper analysisLogMapper,
                                            AnalysisParagraphLogMapper paragraphLogMapper,
                                            AnalysisMatchLogMapper matchLogMapper) {
        this.analysisLogMapper = analysisLogMapper;
        this.paragraphLogMapper = paragraphLogMapper;
        this.matchLogMapper = matchLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createProcessing(AnalysisLogDO log) {
        requireOneRow(analysisLogMapper.insertLog(log), "创建合同分析主记录失败");
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistSuccess(AnalysisLogDO finalLog, ContractCompareResponse response,
                               AnalysisType analysisType) {
        int sequence = 1;
        if (response.getChanges() != null) {
            for (ClauseChange change : response.getChanges()) {
                if (change == null || change.getChangedParagraphs() == null) {
                    continue;
                }
                for (ChangedParagraph paragraph : change.getChangedParagraphs()) {
                    if (paragraph == null) {
                        continue;
                    }
                    AnalysisParagraphLogDO paragraphLog = paragraphLog(
                            finalLog.getAnalysisId(), analysisType, sequence++, change, paragraph);
                    requireOneRow(paragraphLogMapper.insertLog(paragraphLog),
                            "保存合同分析段落失败");
                    saveReferences(finalLog.getAnalysisId(), paragraphLog,
                            paragraph.getBusinessTypePrediction());
                }
            }
        }
        requireOneRow(analysisLogMapper.updateLog(finalLog), "更新合同分析主记录失败");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(String analysisId, long totalCostMs, String errorMessage) {
        AnalysisLogDO log = new AnalysisLogDO();
        log.setAnalysisId(analysisId);
        log.setStatus(AnalysisLogStatus.FAILED.name());
        log.setTotalCostMs(totalCostMs);
        log.setErrorMessage(errorMessage);
        log.setFinishTime(new Date());
        requireOneRow(analysisLogMapper.updateLog(log), "更新合同分析失败状态失败");
    }

    private AnalysisParagraphLogDO paragraphLog(String analysisId, AnalysisType analysisType,
                                                 int sequence, ClauseChange change,
                                                 ChangedParagraph paragraph) {
        AnalysisParagraphLogDO log = new AnalysisParagraphLogDO();
        log.setAnalysisId(analysisId);
        log.setParagraphId(analysisType.name() + "-" + sequence);
        log.setClauseNo(change.getClauseNo());
        log.setClauseTitle(change.getClauseTitle());
        log.setParentClauseNo(change.getParentClauseNo());
        log.setSourceHeading(change.getSourceHeading());
        log.setTargetClauseReference(change.getTargetClauseReference());
        log.setChangeType(paragraph.getParagraphChangeType() == null
                ? null : paragraph.getParagraphChangeType().name());
        log.setOldContent(paragraph.getOldContent());
        log.setNewContent(paragraph.getNewContent());
        log.setPredictionText(paragraph.getParagraphChangeType() == ChangeType.DELETED
                ? paragraph.getOldContent() : paragraph.getNewContent());
        BusinessTypePrediction prediction = paragraph.getBusinessTypePrediction();
        if (prediction != null) {
            log.setPredictionStatus(prediction.getStatus());
            log.setInputScope(prediction.getInputScope());
            log.setChangeTypeCodes(joinPredictionCodes(prediction.getChangeTypes()));
            log.setMaxSimilarity(prediction.getMaxSimilarity());
            log.setMatchType(prediction.getMatchType());
            log.setFallbackUsed(prediction.isFallbackUsed() ? 1 : 0);
        } else {
            log.setFallbackUsed(0);
        }
        log.setCreateTime(new Date());
        return log;
    }

    private void saveReferences(String analysisId, AnalysisParagraphLogDO paragraphLog,
                                BusinessTypePrediction prediction) {
        if (prediction == null || prediction.getReferences() == null) {
            return;
        }
        List<PredictionReference> references = prediction.getReferences();
        for (int index = 0; index < references.size(); index++) {
            PredictionReference reference = references.get(index);
            if (reference == null) {
                continue;
            }
            AnalysisMatchLogDO match = new AnalysisMatchLogDO();
            match.setAnalysisId(analysisId);
            match.setParagraphLogId(paragraphLog.getId());
            match.setHistoricalSampleId(reference.getSampleId());
            match.setSimilarity(reference.getSimilarity());
            match.setChangeTypeCodes(joinCodes(reference.getChangeTypeCodes()));
            match.setRankNo(index + 1);
            match.setCreateTime(new Date());
            requireOneRow(matchLogMapper.insertLog(match), "保存合同分析匹配证据失败");
        }
    }

    private String joinPredictionCodes(List<ChangeTypePrediction> values) {
        if (values == null) {
            return null;
        }
        Set<String> codes = new LinkedHashSet<String>();
        for (ChangeTypePrediction value : values) {
            if (value != null && value.getCode() != null && !value.getCode().trim().isEmpty()) {
                codes.add(value.getCode().trim());
            }
        }
        return joinCodes(codes);
    }

    private String joinCodes(Iterable<String> values) {
        if (values == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(value.trim());
        }
        return result.length() == 0 ? null : result.toString();
    }

    private void requireOneRow(int rows, String message) {
        if (rows != 1) {
            throw new ContractChangeBusinessException(message);
        }
    }
}
