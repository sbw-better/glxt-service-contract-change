package com.citics.glxt.contractchange.contractcompare.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/** 合同双版本比较请求。 */
@Data
public class ContractCompareRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "修改前合同文件路径不能为空")
    private String oldFileGetPath;

    @NotBlank(message = "修改后合同文件路径不能为空")
    private String newFileGetPath;

    @ApiModelProperty(value = "结果模式：SIMPLE仅返回变化段落，CONTEXT额外返回完整条款上下文",
            allowableValues = "SIMPLE, CONTEXT", example = "SIMPLE")
    private ResultMode resultMode = ResultMode.SIMPLE;

    public enum ResultMode {
        SIMPLE, CONTEXT
    }
}
