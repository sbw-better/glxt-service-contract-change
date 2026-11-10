package com.citics.glxt.contractchange.common.page;

import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 某些特殊情况下，不能直接使用mybatis的pageHelper插件，需要自定义对list封装成PageInfo
 * </p>
 *
 * @author wuxingkun
 * @since 2025-01-17
 */
@Data
@ApiModel(value = "自定义pageHelper分页查询结果")
public class PageInfoResult implements Serializable {

    private static final long serialVersionUID = 465653875682684310L;

    //当前页
    private int pageNum;

    //每页的数量
    private int pageSize;

    //当前页的数量
    private int size;

    //当前页展示的数据的起始行
    private int startRow;

    //当前页展示的数据的结束行
    private int endRow;

    //总记录数--所需要进行分页的数据条数
    private long total;

    //总页数
    private int pages;

    //页面展示的结果集，比如说当前页要展示20条数据，则此list为这20条数据
    private List<Map<String, Object>> list;

    //前一页页码
    private int prePage;

    //下一页页码
    private int nextPage;

    //是否为第一页，默认为false，是第一页则设置为true
    private boolean isFirstPage;

    //是否为最后一页默认false，是最后一页则设置为true
    private boolean isLastPage;

    //是否有前一页，默认为false，有前一页则设置为true
    private boolean hasPreviousPage;

    //是否有下一页，默认为false，有后一页则设置为true
    private boolean hasNextPage;

    //导航页码数，所谓导航页码数，就是在页面进行展示的那些1.2.3.4...
    //比如一共有分为两页数据的话，则将此值设置为2
    private int navigatePages;

    //所有导航页号，一共有两页的话则为[1,2]
    private int[] navigatepageNums;

    //导航条上的第一页页码值
    private int navigateFirstPage;

    //导航条上的最后一页页码值
    private int navigateLastPage;

    public PageInfoResult(List<Map<String, Object>> list) {
        this.list = list;
    }

    public void setIsFirstPage(boolean b) {
        this.isFirstPage = b;
    }

    public void setIsLastPage(boolean b) {
        this.isLastPage = b;
    }
}
