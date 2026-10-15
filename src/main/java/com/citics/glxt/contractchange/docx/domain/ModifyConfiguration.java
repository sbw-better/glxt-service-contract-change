package com.citics.glxt.contractchange.docx.domain;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("TPIF_HTXGPZB")
@KeySequence(value = "SEQ_TPIF_HTXGPZB")
@ApiModel(value = "合同解析配置表", description = "合同解析配置表")
public class ModifyConfiguration extends Model<ModifyConfiguration> {

    private static final long serialVersionUID = -6165099072880507261L;

    @JsonIgnore
    public boolean isNew() {
        if (null == id || -1L == id) {
            return true;
        }
        return false;
    }

    @TableId("ID")
    @ApiModelProperty("主键")
    private Long id;

    @TableField("FA")
    @ApiModelProperty("方案")
    private String plan;

    @TableField("MBLX")
    @ApiModelProperty("模板类型")
    private Long templateType;

    @TableField("MK")
    @ApiModelProperty("模块")
    private Long module;

    @TableField("DWFS")
    @ApiModelProperty("定位方式")
    private Long locationType;

    @TableField("PPJJBT")
    @ApiModelProperty("匹配到几级标题")
    private int matchToWhichLevelTitle;

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

    @TableField("XGFSFL")
    @ApiModelProperty("修改方式分类")
    private Long modifyType;

    @TableField("GJZ")
    @ApiModelProperty("关键字")
    private String keyWord;

    @TableField("GJZBGLX")
    @ApiModelProperty("文本或段落变更类型")
    private Long keyWordModifyType;

    @TableField("GJZBGWB")
    @ApiModelProperty("关键字处变更文本")
    private String keyWordModifyText;

    @TableField("BGBGLX")
    @ApiModelProperty("表格变更类型")
    private Long tableModifyType;

    @TableField("SFQY")
    @ApiModelProperty("是否启用")
    private Long isUsed;

    @TableField("XGSX")
    @ApiModelProperty("修改顺序")
    private Long modifyNum;

    @TableField("DLYCZBZX")
    @ApiModelProperty("段落已存在则不新增")
    private Long isNotSure;
}
