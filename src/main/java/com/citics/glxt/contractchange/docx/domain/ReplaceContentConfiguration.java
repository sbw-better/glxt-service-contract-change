package com.citics.glxt.contractchange.docx.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@TableName("TPIF_WDNRTHPZB")
@ApiModel(value = "文档内容替换配置表", description = "文档内容替换配置表")
public class ReplaceContentConfiguration extends Model<ReplaceContentConfiguration> {

    private static final long serialVersionUID = -1965259619505327584L;

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

    @TableField("YNR")
    @ApiModelProperty("原内容")
    private String oldContent;

    @TableField("XNR")
    @ApiModelProperty("新内容")
    private String newContent;

    @TableField("THLX")
    @ApiModelProperty("替换类型")
    private Integer replaceType;

    @TableField("THFW")
    @ApiModelProperty("替换范围")
    private Integer replaceRange;

    @TableField("ZDZYJBT")
    @ApiModelProperty("指定章节一级标题")
    private String designatedFirstTitle;

    @TableField("ZDZEJBT")
    @ApiModelProperty("指定章节二级标题")
    private String designatedSecondTitle;

    @TableField("GJZKS")
    @ApiModelProperty("关键字开始")
    private String keywordStart;

    @TableField("GJZKSSFBL")
    @ApiModelProperty("关键字开始是否保留")
    private Integer keywordStartKeep;

    @TableField("GJZJS")
    @ApiModelProperty("关键字结束")
    private String keywordEnd;

    @TableField("GJZJSSFBL")
    @ApiModelProperty("关键字结束是否保留")
    private Integer keywordEndKeep;

    @TableField("INSTID")
    @ApiModelProperty("流程INSTID")
    private Long instid;

    @TableField("CPID")
    @ApiModelProperty("产品ID")
    private Long fundId;

    @TableField("HTDXMC")
    @ApiModelProperty("合同对象名称")
    private String fileObjectName;

    @TableField("HTDXID")
    @ApiModelProperty("合同对象ID")
    private String fileObjectId;

    @TableField("SCSJ")
    @ApiModelProperty("生成时间")
    private Date createdTime;
}
