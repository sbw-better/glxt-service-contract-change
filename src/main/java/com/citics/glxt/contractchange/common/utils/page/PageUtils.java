package com.citics.glxt.contractchange.common.utils.page;

import com.github.pagehelper.PageInfo;
import com.citics.glxt.contractchange.common.page.PageResult;

import java.util.List;

public class PageUtils {

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
