package com.citics.glxt.contractchange.docx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("TPIF_HTJXZB_HTJXMXJL")
@KeySequence(value = "SEQ_TPIF_HTJXZB_HTJXMXJL")
@ApiModel(value = "合同解析明细表", description = "合同解析明细表")
public class ContractAnalysisDetail extends Model<ContractAnalysisDetail> {

    private static final long serialVersionUID = -1010700970997153785L;

    @JsonIgnore
    public boolean isNew(){
        if(null == id || -1L == id) {
            return true;
        }
        return false;
    }

    @TableId(value = "ID", type = IdType.INPUT)
    @ApiModelProperty("主键")
    private Long id;

    @TableField("TPIF_HTJXZB_ID")
    @ApiModelProperty("主表ID")
    private Long masterId;

    @TableField("MK")
    @ApiModelProperty("模块")
    private Long module;

    @TableField("ZJMC")
    @ApiModelProperty("章节名称")
    private String chapterName;

    @TableField("SFHBTQZ")
    @ApiModelProperty("是否含标题前缀")
    private int isBelongTitle;

    @TableField("HQZSBTJB")
    @ApiModelProperty("含前缀时标题级别")
    private String titleLevel;

    @TableField("HQZSBT")
    @ApiModelProperty("含前缀时标题")
    private String titlePrefix;

    @TableField("YJBT")
    @ApiModelProperty("一级标题")
    private String firstTitle;

    @TableField("EJBT")
    @ApiModelProperty("二级标题")
    private String secondTitle;

    @TableField("SANJBT")
    @ApiModelProperty("三级标题")
    private String thirdTitle;

    @TableField("SIJBT")
    @ApiModelProperty("四级标题")
    private String fourthTitle;

    @TableField("WJBT")
    @ApiModelProperty("五级标题")
    private String fifthTitle;

    @TableField("DLXH")
    @ApiModelProperty("段落序号")
    private String contentNumber;

    @TableField("DLCJXX")
    @ApiModelProperty("段落层级信息")
    private String paraLevelInfo;

    @TableField("YDLNR")
    @ApiModelProperty("原段落内容")
    private String originalContent;

    @TableField("XDLNR")
    @ApiModelProperty("新段落内容")
    private String newContent;

    @TableField("SFXD")
    @ApiModelProperty("是否有修订")
    private Long haveModify;

    @TableField("XDLX")
    @ApiModelProperty("修订类型")
    private Long modifyType;

    @TableField("PZBH")
    @ApiModelProperty("批注编号")
    private String annotationNumber;

    @TableField("YM")
    @ApiModelProperty("页码")
    private String pageNumber;

}
