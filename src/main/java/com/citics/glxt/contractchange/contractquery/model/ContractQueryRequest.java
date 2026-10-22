package com.citics.glxt.contractchange.contractquery.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/** 接口一 JSON 请求体。 */
@ApiModel("合同文档查询请求")
@Data
public class ContractQueryRequest {
    @NotNull(message = "管理人ID不能为空")
    @ApiModelProperty(value = "管理人唯一ID", required = true, example = "525")
    private Long managerId;

    @NotNull(message = "查询基准日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "查询基准日期", required = true, example = "2026-10-09")
    private LocalDate baseDate;

    @NotNull(message = "查询天数不能为空")
    @Min(value = 0, message = "查询天数不能小于0")
    @Max(value = 6, message = "查询天数不能大于6")
    @ApiModelProperty(value = "查询天数，允许0到6；不传默认0", example = "3")
    private Integer queryDays = 0;
}
