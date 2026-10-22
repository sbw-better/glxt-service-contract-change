package com.citics.glxt.contractchange.contractquery.model;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/** 按管理人和一组文档编号下载合同文件。 */
@Data
public class ContractDownloadRequest {
    @NotNull(message = "管理人ID不能为空")
    @Min(value = 1, message = "管理人ID必须为正数")
    private Long managerId;

    @NotEmpty(message = "文件编号集合不能为空")
    private List<@NotNull(message = "文件编号不能为空") @Min(value = 1, message = "文件编号必须为正数") Long> fileIds;
}
