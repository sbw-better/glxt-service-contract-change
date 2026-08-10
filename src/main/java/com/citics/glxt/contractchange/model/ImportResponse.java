package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 历史样本同步导入结果。 */
@Data
@AllArgsConstructor
public class ImportResponse {
    /** 本批次是否通过校验并成功完成数据库写入。 */
    private boolean success;
    /** Excel 中实际读取的非空数据行数。 */
    private int totalRows;
    /** 本批次新增数据库记录数。 */
    private int inserted;
    /** Excel 内或数据库中标签一致的重复记录数。 */
    private int skipped;
    /** 入库后是否成功切换到新内存索引。 */
    private boolean indexReloaded;
    /** 校验或标签冲突错误；成功时为空列表。 */
    private List<ImportErrorItem> errors;
}
