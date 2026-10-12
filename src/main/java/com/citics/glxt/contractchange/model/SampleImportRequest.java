package com.citics.glxt.contractchange.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/** 从主备SFTP服务器读取历史段落Excel的请求。 */
@Data
public class SampleImportRequest {
    @NotBlank(message = "Excel文件路径不能为空")
    @ApiModelProperty(value = "Excel文件的SFTP服务器路径，仅支持xlsx", required = true)
    private String fileGetPath;

    @NotBlank(message = "userId不能为空")
    @ApiModelProperty(value = "实际操作人工号，透传给模型平台", required = true)
    private String userId;
}
