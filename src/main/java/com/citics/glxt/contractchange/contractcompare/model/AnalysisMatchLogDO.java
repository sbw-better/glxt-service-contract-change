package com.citics.glxt.contractchange.contractcompare.model;

import java.io.Serializable;
import java.util.Date;

public class AnalysisMatchLogDO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String analysisId;
    private Long paragraphLogId;
    private Long historicalSampleId;
    private Double similarity;
    private String changeTypeCodes;
    private Integer rankNo;
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

    public Long getParagraphLogId() {
        return paragraphLogId;
    }

    public void setParagraphLogId(Long paragraphLogId) {
        this.paragraphLogId = paragraphLogId;
    }

    public Long getHistoricalSampleId() {
        return historicalSampleId;
    }

    public void setHistoricalSampleId(Long historicalSampleId) {
        this.historicalSampleId = historicalSampleId;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public String getChangeTypeCodes() {
        return changeTypeCodes;
    }

    public void setChangeTypeCodes(String changeTypeCodes) {
        this.changeTypeCodes = changeTypeCodes;
    }

    public Integer getRankNo() {
        return rankNo;
    }

    public void setRankNo(Integer rankNo) {
        this.rankNo = rankNo;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
