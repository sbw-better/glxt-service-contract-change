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
@TableName("TPIF_WDNRTHKXFWPZB")
@ApiModel(
        value = "文档内容替换-可选范围配置表",
        description = "文档内容替换-可选范围配置表"
)
public class ReplaceContentConfigRange extends Model<ReplaceContentConfigRange> {

    private static final long serialVersionUID = 7809285428421202472L;

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

    @TableField("BLM")
    @ApiModelProperty("变量名")
    private String paramName;

    @TableField("BLZ")
    @ApiModelProperty("变量值")
    private String paramValue;

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
