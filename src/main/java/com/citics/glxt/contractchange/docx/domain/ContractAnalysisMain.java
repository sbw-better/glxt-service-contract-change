package com.citics.glxt.contractchange.docx.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@NoArgsConstructor
@TableName("TPIF_HTJXZB")
@KeySequence(value = "SEQ_TPIF_HTJXZB")
@ApiModel(value = "合同解析主表", description="合同解析主表")
public class ContractAnalysisMain extends Model<ContractAnalysisMain> {

    private static final long serialVersionUID = -6803903591137452240L;

    @JsonIgnore
    public boolean isNew(){
        if(null == id || -1L == id) {
            return true;
        }
        return false;
    }

    @TableId("ID")
    @ApiModelProperty("主键")
    private Long id;

    @TableField("LX")
    @ApiModelProperty("类型")
    private Long type;

    @TableField("CPID")
    @ApiModelProperty("产品ID")
    private Long fundId;

    @TableField("HTMC")
    @ApiModelProperty("合同名称")
    private String contractName;

    @TableField("HTWDFL")
    @ApiModelProperty("合同文档分类")
    private Long contractType;

    @TableField("HTPBBH")
    @ApiModelProperty("合同PBBH")
    private Long contractPbbh;

    @TableField("HTSBBH")
    @ApiModelProperty("HTSBBH")
    private Long contractSbbh;

    @TableField("HTSZBM")
    @ApiModelProperty("合同所在表名")
    private String contractTableName;

    @TableField("HTSZBZD")
    @ApiModelProperty("合同所在表字段")
    private String contractTableField;

    @TableField("HTSZBID")
    @ApiModelProperty("合同所在表ID")
    private Long contractTableId;

    @TableField("BCXYMC")
    @ApiModelProperty("补充协议名称")
    private String additionName;

    @TableField("BCXYWDFL")
    @ApiModelProperty("补充协议文档分类")
    private Long additionType;

    @TableField("BCXYPBBH")
    @ApiModelProperty("补充协议PBBH")
    private Long additionPbbh;

    @TableField("BCXYSBBH")
    @ApiModelProperty("补充协议SBBH")
    private Long additionSbbh;

    @TableField("BCXYSZBM")
    @ApiModelProperty("补充协议所在表名")
    private String additionTableName;

    @TableField("BCXYSZBZD")
    @ApiModelProperty("补充协议所在表字段")
    private String additionTableField;

    @TableField("BCXYSZBID")
    @ApiModelProperty("补充协议所在表ID")
    private Long additionTableId;

    @TableField("SCXWDSZBM")
    @ApiModelProperty("生成新文档所在表名")
    private String newFileTableName;

    @TableField("SCXWDSZBZD")
    @ApiModelProperty("生成新文档所在字段")
    private String newFileTableField;

    @TableField("SCXWDSZBID")
    @ApiModelProperty("生成新文档所在表ID")
    private Long newFileTableId;

    @TableField("WJLJ")
    @ApiModelProperty("文件路径")
    private String fileGetPath;

    @TableField("SCXWJLJ")
    @ApiModelProperty("生成新文件路径")
    private String fileSavePath;

    @TableField("INSTID")
    @ApiModelProperty("流程INSTID")
    private Long instid;

    @TableField("JXSJ")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    @ApiModelProperty("解析时间")
    private Date analysisTime;

}

