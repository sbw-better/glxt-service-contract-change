package com.citics.glxt.contractchange.contractquery.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/** 分页查询合同文档请求；查询条件与普通查询保持一致。 */
@Setter
@Getter
@ApiModel("合同文档分页查询请求")
public class ContractPageQueryRequest {
    private static final long serialVersionUID = 1L;

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
    private Integer queryDays = 0;

    @NotNull(message = "当前页码不能为空")
    @Min(value = 1, message = "当前页码必须大于等于1")
    @ApiModelProperty(value = "当前页码，从1开始；省略时默认为1", example = "1")
    private Integer pageNum = 1;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 100, message = "每页数量不能大于100")
    @ApiModelProperty(value = "每页数量，范围1到100", required = true, example = "20")
    private Integer pageSize;

}
