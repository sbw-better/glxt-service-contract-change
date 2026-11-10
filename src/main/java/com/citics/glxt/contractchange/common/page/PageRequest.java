package com.citics.glxt.contractchange.common.page;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求
 */
@Data
@ApiModel(value = "分页查询入参")
public class PageRequest implements Serializable {

    private static final long serialVersionUID = -4622759152174895562L;

    /**
     * 当前页码
     */
    @ApiModelProperty(value = "当前页码（起始页为1）")
    private int pageNum = 1;

    /**
     * 每页数量
     */
    @ApiModelProperty(value = "每页数量")
    private int pageSize = 0;

}
