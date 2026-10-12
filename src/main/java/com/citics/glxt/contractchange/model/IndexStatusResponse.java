package com.citics.glxt.contractchange.model;

import lombok.Data;

import java.util.Date;

/** 当前 JVM 内存索引的可观测状态。 */
@Data
public class IndexStatusResponse {
    /** NOT_READY、READY、EMPTY、DEGRADED 或 LOAD_FAILED。 */
    private String status;
    /** 成功加载且实际参与检索的样本数。 */
    private int sampleCount;
    /** 当前索引绑定的模型版本。 */
    private String modelVersion;
    /** 当前索引向量维度。 */
    private int vectorDimension;
    /** 当前快照生成时间。 */
    private Date loadedAt;
    /** 重载时跳过的损坏记录数。 */
    private int errorCount;
    private String activeModelVersion;
    private String configuredModelVersion;
    private String operationId;
    private String operationStatus;
    private boolean recognitionAvailable;

    public IndexStatusResponse(String status,int sampleCount,String modelVersion,int vectorDimension,
            Date loadedAt,int errorCount) {
        this.status=status; this.sampleCount=sampleCount; this.modelVersion=modelVersion;
        this.vectorDimension=vectorDimension; this.loadedAt=loadedAt; this.errorCount=errorCount;
        this.recognitionAvailable="READY".equals(status) || "EMPTY".equals(status)
                || ("DEGRADED".equals(status) && sampleCount>0);
    }
}
