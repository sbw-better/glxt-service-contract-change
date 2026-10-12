package com.citics.glxt.contractchange.domain;

import lombok.Data;

/** 单实例样本库的持久化版本身份和跨请求写入租约（无超时自动抢占）。 */
@Data
public class SampleLibraryControlDO {
    private String modelName;
    private String modelVersion;
    private Integer vectorDim;
    private long revision;
    private String operationId;
    /** IDLE、RUNNING、DB_COMMITTED；只保存当前操作，不保存任务历史。 */
    private String operationStatus;
    /** 升级提交后严格恢复索引所需数量，其它状态为空。 */
    private Integer expectedSamples;
}
