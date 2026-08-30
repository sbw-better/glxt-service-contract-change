package com.citics.glxt.contractchange.docx.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.io.Serializable;

@Data
@ApiModel(value="合同处理入参dto", description="")
public class DocxAnalysisDTO implements Serializable {

    private static final long serialVersionUID = 5207213186902018442L;

    /**
     * ********************公共入参：********************
     */
    /**
     * 1|根据修订记录生成补充协议
     * 2|根据补充协议生成合同
     * 3|运作指引整改协商函生成合同
     */
    @ApiModelProperty("操作类型")
    private Long operateType;

    /**
     * ********************运作指引整改协商函生成新版合同：********************
     */
    /**
     * 1|方案1
     * 2|方案2
     * 3|方案3
     */
    @ApiModelProperty("运作指引整改协商函生成合同--方案")
    private Long plan;

    @ApiModelProperty("运作指引整改协商函生成合同--合同编号")
    private String versionCode;

    @ApiModelProperty("运作指引整改协商函生成合同--合同文件获取地址")
    private String fileGetPath;

    @ApiModelProperty("运作指引整改协商函生成合同--合同文件保存地址")
    private String fileSavePath;

    @ApiModelProperty("运作指引整改协商函生成合同--管理人TA账户是否已确认")
    private Integer managerTaIsConfirm;

    @ApiModelProperty("运作指引整改协商函生成合同--是否是中金公司")
    private Boolean isZhongJin;

    /**
     * ********************根据修订记录生成补充协议：********************
     */
    @ApiModelProperty("根据修订记录生成补充协议--修订记录文件获取地址")
    private String revisionFileGetPath;

    @ApiModelProperty("根据修订记录生成补充协议--补充协议文件获取地址")
    private String supplementFileGetPath;

    @ApiModelProperty("根据修订记录生成补充协议--补充协议文件保存地址")
    private String supplementFileSavePath;

    /**
     * ********************文档内容替换：********************
     */
    @ApiModelProperty("文档内容替换--合同文件获取地址")
    private String replaceFileGetPath;

    @ApiModelProperty("文档内容替换--合同文件保存地址")
    private String replaceFileSavePath;

    @ApiModelProperty("文档内容替换--修改配置ID集合（TPIF_WDNRTHPZB对象ID）")
    private String replaceConfigIds;

    @ApiModelProperty("文档内容替换--可选范围配置表ID集合（TPIF_WDNRTHKXFWPZB对象ID）")
    private String replaceConfigRangeIds;

    @ApiModelProperty("文档内容替换--是否是纯文本替换")
    private Boolean isAllText;

    @ApiModelProperty("文档内容替换--替换时是否开启修订模式")
    private Boolean isModify;

    /**
     * ********************经办人关键字处理：********************
     */
    @ApiModelProperty("经办人关键字处理--合同文件获取地址")
    private String jbrFileGetPath;

    @ApiModelProperty("经办人关键字处理--合同文件保存地址")
    private String jbrFileSavePath;

    @ApiModelProperty("经办人关键字处理--操作类型")
    private Long jbrType;

    @ApiModelProperty("经办人关键字处理--定位关键字")
    private String jbrKeyWords;

}

