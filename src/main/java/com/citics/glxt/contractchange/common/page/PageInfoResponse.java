package com.citics.glxt.contractchange.common.page;

import io.swagger.annotations.ApiModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 通用分页响应数据，字段结构与 PageHelper PageInfo 保持一致。 */
@Data
@ApiModel("通用分页查询结果")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PageInfoResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int pageNum;
    private int pageSize;
    private int size;
    private int startRow;
    private int endRow;
    private long total;
    private int pages;
    private List<T> list;
    private int prePage;
    private int nextPage;
    private boolean hasPreviousPage;
    private boolean hasNextPage;
    private int navigatePages;
    private int[] navigatepageNums;
    private int navigateFirstPage;
    private int navigateLastPage;
    private boolean lastPage;
    private boolean firstPage;
}
