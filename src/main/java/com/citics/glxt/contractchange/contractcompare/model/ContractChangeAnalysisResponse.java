package com.citics.glxt.contractchange.contractcompare.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContractChangeAnalysisResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String analysisId;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<String> fileChangeTypeCodes = Collections.<String>emptyList();
    private AnalysisLogStatus analysisStatus;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String failureReason;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer failureCode;

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public List<String> getFileChangeTypeCodes() {
        return fileChangeTypeCodes;
    }

    public void setFileChangeTypeCodes(List<String> fileChangeTypeCodes) {
        this.fileChangeTypeCodes = fileChangeTypeCodes == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(fileChangeTypeCodes);
    }

    public AnalysisLogStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(AnalysisLogStatus analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Integer getFailureCode() { return failureCode; }
    public void setFailureCode(Integer failureCode) { this.failureCode = failureCode; }

    public ContractChangeAnalysisResponse() {
    }

    public ContractChangeAnalysisResponse(String analysisId, List<String> fileChangeTypeCodes,
                                          AnalysisLogStatus analysisStatus) {
        this.analysisId = analysisId;
        setFileChangeTypeCodes(fileChangeTypeCodes);
        this.analysisStatus = analysisStatus;
    }

}
