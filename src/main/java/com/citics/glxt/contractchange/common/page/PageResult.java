package com.citics.glxt.contractchange.common.page;

import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页返回结果
 */
@Data
@ApiModel(value = "分页查询结果")
public class PageResult implements Serializable {

    private static final long serialVersionUID = -6513514609724608603L;

    /**
     * 当前页码
     */
    private int pageNum;

    /**
     * 每页数量
     */
    private int pageSize;

    /**
     * 记录总数
     */
    private long totalSize;

    /**
     * 页码总数
     */
    private int totalPages;

    /**
     * 数据模型
     */
    private List<?> content;
}
