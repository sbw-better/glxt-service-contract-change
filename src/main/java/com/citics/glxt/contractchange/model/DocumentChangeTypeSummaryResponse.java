package com.citics.glxt.contractchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 文档解析落地后返回的主记录与文件级变更类型汇总。 */
@Data
@AllArgsConstructor
public class DocumentChangeTypeSummaryResponse {
    /** TPIF_HTJXZB 主表ID。 */
    private Long mainId;
    /** 按字符串升序排列的文件级变更类型编码。 */
    private List<String> fileChangeTypeCodes;
}
