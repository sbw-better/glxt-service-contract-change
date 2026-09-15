package com.citics.glxt.contractchange.contractcompare.model;

import com.citics.glxt.contractchange.contractcompare.validation.ValidContractCompareRequest;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/** 合同双版本比较或变更函提取请求。 */
@Data
@ValidContractCompareRequest
public class ContractCompareRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String oldFileGetPath;

    private String newFileGetPath;

    @ApiModelProperty(value = "分析类型：DOUBLE_VERSION双版本比较，CHANGE_DOCUMENT单文件变更函提取",
            allowableValues = "DOUBLE_VERSION, CHANGE_DOCUMENT", example = "DOUBLE_VERSION")
    private AnalysisType analysisType = AnalysisType.DOUBLE_VERSION;

    @ApiModelProperty(value = "单文件变更函服务器路径，CHANGE_DOCUMENT时必填")
    private String changeFileGetPath;

    @ApiModelProperty(value = "双版本结果模式：SIMPLE仅返回变化段落，CONTEXT额外返回完整条款上下文；单文件模式忽略",
            allowableValues = "SIMPLE, CONTEXT", example = "SIMPLE")
    private ResultMode resultMode = ResultMode.SIMPLE;

    public enum ResultMode {
        SIMPLE, CONTEXT
    }

    public enum AnalysisType {
        DOUBLE_VERSION, CHANGE_DOCUMENT
    }

}
