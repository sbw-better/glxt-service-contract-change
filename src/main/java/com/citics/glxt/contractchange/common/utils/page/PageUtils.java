package com.citics.glxt.contractchange.common.utils.page;

import com.github.pagehelper.PageInfo;
import com.citics.glxt.contractchange.common.page.PageInfoResponse;
import com.citics.glxt.contractchange.common.page.PageResult;

import java.util.List;

public class PageUtils {

    /** 将 PageHelper 元数据转换为可跨业务复用的分页响应结构。 */
    public static <T> PageInfoResponse<T> getPageInfoResponse(PageInfo<T> pageInfo) {
        PageInfoResponse<T> response = new PageInfoResponse<>();
        response.setPageNum(pageInfo.getPageNum());
        response.setPageSize(pageInfo.getPageSize());
        response.setSize(pageInfo.getSize());
        response.setStartRow(pageInfo.getStartRow());
        response.setEndRow(pageInfo.getEndRow());
        response.setTotal(pageInfo.getTotal());
        response.setPages(pageInfo.getPages());
        response.setList(pageInfo.getList());
        response.setPrePage(pageInfo.getPrePage());
        response.setNextPage(pageInfo.getNextPage());
        response.setHasPreviousPage(pageInfo.isHasPreviousPage());
        response.setHasNextPage(pageInfo.isHasNextPage());
        response.setNavigatePages(pageInfo.getNavigatePages());
        response.setNavigatepageNums(pageInfo.getNavigatepageNums());
        response.setNavigateFirstPage(pageInfo.getNavigateFirstPage());
        response.setNavigateLastPage(pageInfo.getNavigateLastPage());
        response.setLastPage(pageInfo.isIsLastPage());
        response.setFirstPage(pageInfo.isIsFirstPage());
        return response;
    }

    /**
     * 将分页信息封装到统一的接口
     */
    public static PageResult getPageResult(PageInfo<?> pageInfo, List<?> list) {
        PageResult pageResult = new PageResult();
        pageResult.setPageNum(pageInfo.getPageNum());
        pageResult.setPageSize(pageInfo.getPageSize());
        pageResult.setTotalSize(pageInfo.getTotal());
        pageResult.setTotalPages(pageInfo.getPages());
        pageResult.setContent(list);
        return pageResult;
    }

    public static PageResult getPageResult(PageInfo<?> pageInfo) {
        PageResult pageResult = new PageResult();
        pageResult.setPageNum(pageInfo.getPageNum());
        if (pageInfo.getPageSize() == 0) {
            pageResult.setPageSize((int) Math.min(Integer.MAX_VALUE, pageInfo.getTotal()));
        } else {
            pageResult.setPageSize(pageInfo.getPageSize());
        }
        pageResult.setTotalSize(pageInfo.getTotal());
        pageResult.setTotalPages(pageInfo.getPages());
        pageResult.setContent(pageInfo.getList());
        return pageResult;
    }
}
