package com.citics.glxt.contractchange.contractcompare.model;

/** 一次合同分析任务的执行状态。 */
public enum AnalysisLogStatus {
    PROCESSING, SUCCESS,
    /** 保留历史记录兼容；当前分析流程不再生成此状态。 */
    PARTIAL_SUCCESS,
    FAILED
}
