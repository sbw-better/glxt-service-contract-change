package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Excel 单行导入错误。 */
@Data
@AllArgsConstructor
public class ImportErrorItem {
    /** 从 1 开始的 Excel 行号。 */
    private int row;
    /** 可直接展示给操作人员的错误原因。 */
    private String message;
}
