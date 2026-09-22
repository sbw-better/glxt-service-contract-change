package com.citics.glxt.contractchange.contractcompare.model;

import java.io.Serializable;
import java.util.Date;

public class AnalysisLogDO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String analysisId;
    private Long instId;
    private String userId;
    private String analysisType;
    private String oldFileGetPath;
    private String newFileGetPath;
    private String changeFileGetPath;
    private String status;
    private Integer changedParagraphCount;
    private String changeTypeCodes;
    private String embeddingModel;
    private String modelVersion;
    private Long totalCostMs;
    private Long compareCostMs;
    private Long predictCostMs;
    private String errorMessage;
    private String resultJson;
    private Date createTime;
    private Date finishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public Long getInstId() {
        return instId;
    }

    public void setInstId(Long instId) {
        this.instId = instId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAnalysisType() {
        return analysisType;
    }

    public void setAnalysisType(String analysisType) {
        this.analysisType = analysisType;
    }

    public String getOldFileGetPath() {
        return oldFileGetPath;
    }

    public void setOldFileGetPath(String oldFileGetPath) {
        this.oldFileGetPath = oldFileGetPath;
    }

    public String getNewFileGetPath() {
        return newFileGetPath;
    }

    public void setNewFileGetPath(String newFileGetPath) {
        this.newFileGetPath = newFileGetPath;
    }

    public String getChangeFileGetPath() {
        return changeFileGetPath;
    }

    public void setChangeFileGetPath(String changeFileGetPath) {
        this.changeFileGetPath = changeFileGetPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getChangedParagraphCount() {
        return changedParagraphCount;
    }

    public void setChangedParagraphCount(Integer changedParagraphCount) {
        this.changedParagraphCount = changedParagraphCount;
    }

    public String getChangeTypeCodes() {
        return changeTypeCodes;
    }

    public void setChangeTypeCodes(String changeTypeCodes) {
        this.changeTypeCodes = changeTypeCodes;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Long getTotalCostMs() {
        return totalCostMs;
    }

    public void setTotalCostMs(Long totalCostMs) {
        this.totalCostMs = totalCostMs;
    }

    public Long getCompareCostMs() {
        return compareCostMs;
    }

    public void setCompareCostMs(Long compareCostMs) {
        this.compareCostMs = compareCostMs;
    }

    public Long getPredictCostMs() {
        return predictCostMs;
    }

    public void setPredictCostMs(Long predictCostMs) {
        this.predictCostMs = predictCostMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Date finishTime) {
        this.finishTime = finishTime;
    }

}
