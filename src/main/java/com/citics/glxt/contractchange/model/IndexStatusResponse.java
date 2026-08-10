package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/** 当前 JVM 内存索引的可观测状态。 */
@Data
@AllArgsConstructor
public class IndexStatusResponse {
    /** READY、EMPTY 或 DEGRADED。 */
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
}
