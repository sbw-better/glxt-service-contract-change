package com.citics.glxt.contractchange.common.utils.page;


import com.citics.glxt.contractchange.common.page.PageInfoResult;

import java.util.List;
import java.util.Map;

public class PageHelperUtils {

    //实现list分页。入参是分页查询后的list，并不是全量数据list
    public static PageInfoResult list2PageInfo(List<Map<String, Object>> list, Integer pageNum, Integer pageSize, Integer total) {
        // 列表是否为空
        boolean listEmpty = list == null || list.size() == 0;

        int pageStart = (pageNum - 1) * pageSize; // 前面几页已经装下或能够装下的结果数量
        int pageEnd = Math.min(total, pageSize * pageNum);
        PageInfoResult pageInfo = new PageInfoResult(list);

        //获取PageInfo其他参数
        pageInfo.setTotal(total);

        int starRow = pageStart < total ? pageStart + 1 : 0;
        pageInfo.setStartRow(listEmpty ? 0 : starRow);

        int endRow = pageStart < total ? pageEnd : 0;
        pageInfo.setEndRow(listEmpty ? 0 : endRow);

        boolean hasNextPage = total > pageSize * pageNum;
        pageInfo.setHasNextPage(listEmpty ? false : hasNextPage);

        boolean hasPreviousPage = pageNum != 1;
        pageInfo.setHasPreviousPage(listEmpty ? false : hasPreviousPage);

        pageInfo.setIsFirstPage(listEmpty ? false : !hasPreviousPage);

        boolean isLastPage = total > pageSize * (pageNum - 1) && total <= pageSize * pageNum;
        pageInfo.setIsLastPage(listEmpty ? false : isLastPage);

        int pages = total % pageSize == 0 ? total / pageSize : (total / pageSize) + 1;
        pageInfo.setNavigatePages(listEmpty ? 0 : pages);

        pageInfo.setNavigateLastPage(listEmpty ? 0 : pages);
        pageInfo.setNavigateFirstPage(listEmpty ? 0 : (pages == 0 ? 1 : 1));

        int[] navigatePageNums = new int[pages];
        if (pages > 0) {
            for (int i = 1; i <= pages; i++) {
                navigatePageNums[i - 1] = i;
            }
        }
        pageInfo.setNavigatepageNums(pages == 0 ? null : navigatePageNums);

        int prePage = pageNum - 1;
        pageInfo.setPrePage(listEmpty ? 0 : prePage);

        int nextPage = pageNum < pages ? pageNum + 1 : 0;
        pageInfo.setNextPage(listEmpty ? 0 : nextPage);

        pageInfo.setPageNum(listEmpty ? 0 : pageNum);
        pageInfo.setPageSize(listEmpty ? 0 : pageSize);
        pageInfo.setPages(listEmpty ? 0 : pages);
        pageInfo.setSize(listEmpty ? 0 : pageInfo.getList().size());

        return pageInfo;
    }
}
