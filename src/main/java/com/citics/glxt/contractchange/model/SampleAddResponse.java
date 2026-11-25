package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 单条历史样本新增结果；INDEX_PENDING表示数据库已提交但索引尚待恢复。 */
@Data
@AllArgsConstructor
public class SampleAddResponse {
    /** INSERTED、REACTIVATED、SKIPPED_DUPLICATE或INDEX_PENDING。 */
    private String status;
    /** 样本库写操作编号，可用于日志排查。 */
    private String operationId;
    /** 本次新增记录数。 */
    private int inserted;
    /** 本次重新启用或更新的记录数。 */
    private int updated;
    /** 因同文同标签而跳过的记录数。 */
    private int skipped;
    /** 是否完成入库后的内存索引刷新。 */
    private boolean indexReloaded;
    /** 是否因模型版本变化而重建了全量样本向量。 */
    private boolean rebuild;
}
