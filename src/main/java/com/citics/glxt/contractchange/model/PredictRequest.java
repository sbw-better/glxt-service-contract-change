package com.citics.glxt.contractchange.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 单段合同文本预测请求。 */
@Data
public class PredictRequest {
    /** 已在上游完成切分的新合同段落。 */
    @NotBlank(message = "合同段落不能为空")
    private String paragraph;
}
