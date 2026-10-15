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

@Data
@NoArgsConstructor
@TableName("TPIF_HTXGPZB_BGMX")
@ApiModel(value = "合同解析配置表表格明细", description = "合同解析配置表表格明细")
public class ModifyConfigTableDetail extends Model<ModifyConfigTableDetail> {

    private static final long serialVersionUID = -8871954034770580160L;

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

    @TableField("TPIF_HTXGPZB_ID")
    @ApiModelProperty("主表ID")
    private Long masterId;

    @TableField("HH")
    @ApiModelProperty("行号")
    private Long rowNumber;

    @TableField("LH")
    @ApiModelProperty("列号")
    private Long colNumber;

    @TableField("NR")
    @ApiModelProperty("内容")
    private String content;
}
