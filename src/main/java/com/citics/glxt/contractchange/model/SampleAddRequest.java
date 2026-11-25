package com.citics.glxt.contractchange.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 单条新增历史段落及其变更类型的请求。 */
@Data
public class SampleAddRequest {
    @NotBlank(message = "合同段落不能为空")
    @ApiModelProperty(value = "历史合同段落内容", required = true)
    private String paragraph;

    @NotBlank(message = "变更类型编码不能为空")
    @ApiModelProperty(value = "一个或多个变更类型编码，支持英文或中文分号、逗号分隔", required = true)
    private String changeTypeCodes;

    @NotBlank(message = "userId不能为空")
    @ApiModelProperty(value = "实际操作人工号，透传给模型平台", required = true)
    private String userId;
}
