package com.citics.glxt.contractchange.contractcompare.model;

import java.io.Serializable;
import java.util.Date;

public class AnalysisParagraphLogDO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String analysisId;
    private String paragraphId;
    private String clauseNo;
    private String clauseTitle;
    private String parentClauseNo;
    private String sourceHeading;
    private String targetClauseReference;
    private String changeType;
    private String oldContent;
    private String newContent;
    private String predictionText;
    private String predictionStatus;
    private String inputScope;
    private String changeTypeCodes;
    private Double maxSimilarity;
    private String matchType;
    private Integer fallbackUsed;
    private Date createTime;

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

    public String getParagraphId() {
        return paragraphId;
    }

    public void setParagraphId(String paragraphId) {
        this.paragraphId = paragraphId;
    }

    public String getClauseNo() {
        return clauseNo;
    }

    public void setClauseNo(String clauseNo) {
        this.clauseNo = clauseNo;
    }

    public String getClauseTitle() {
        return clauseTitle;
    }

    public void setClauseTitle(String clauseTitle) {
        this.clauseTitle = clauseTitle;
    }

    public String getParentClauseNo() {
        return parentClauseNo;
    }

    public void setParentClauseNo(String parentClauseNo) {
        this.parentClauseNo = parentClauseNo;
    }

    public String getSourceHeading() {
        return sourceHeading;
    }

    public void setSourceHeading(String sourceHeading) {
        this.sourceHeading = sourceHeading;
    }

    public String getTargetClauseReference() {
        return targetClauseReference;
    }

    public void setTargetClauseReference(String targetClauseReference) {
        this.targetClauseReference = targetClauseReference;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getOldContent() {
        return oldContent;
    }

    public void setOldContent(String oldContent) {
        this.oldContent = oldContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }

    public String getPredictionText() {
        return predictionText;
    }

    public void setPredictionText(String predictionText) {
        this.predictionText = predictionText;
    }

    public String getPredictionStatus() {
        return predictionStatus;
    }

    public void setPredictionStatus(String predictionStatus) {
        this.predictionStatus = predictionStatus;
    }

    public String getInputScope() {
        return inputScope;
    }

    public void setInputScope(String inputScope) {
        this.inputScope = inputScope;
    }

    public String getChangeTypeCodes() {
        return changeTypeCodes;
    }

    public void setChangeTypeCodes(String changeTypeCodes) {
        this.changeTypeCodes = changeTypeCodes;
    }

    public Double getMaxSimilarity() {
        return maxSimilarity;
    }

    public void setMaxSimilarity(Double maxSimilarity) {
        this.maxSimilarity = maxSimilarity;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public Integer getFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(Integer fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

}
