package com.citics.glxt.contractchange.model;

import lombok.Getter;

/** 同步重建全部完成后返回；入库数量不是仅本次Excel新增数量。 */
@Getter
public class SampleRebuildResponse {
    private final boolean success = true;
    private final boolean rebuild = true;
    private final boolean indexReloaded = true;
    private final String operationId;
    private final String sourceVersion;
    private final String targetVersion;
    private final int totalSamples;
    private final int insertedSamples;
    private final int disabledSamples;

    public SampleRebuildResponse(String operationId,String sourceVersion,String targetVersion,int total,int disabled) {
        this.operationId=operationId; this.sourceVersion=sourceVersion; this.targetVersion=targetVersion;
        totalSamples=total; insertedSamples=total; disabledSamples=disabled;
    }
}
