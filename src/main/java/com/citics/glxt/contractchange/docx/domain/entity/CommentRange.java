package com.citics.glxt.contractchange.docx.domain.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class CommentRange {

    // 批注ID
    String commentId;

    // 批注起始段落或表格
    EnhanceObj startObj;

    // 批注结束段落或表格
    EnhanceObj endObj;

    // 范围内所有段落或表格
    List<EnhanceObj> ObjInRange;

    // 范围内所有段落，如果有是标题的段落，记录下标题级别
    Set<Integer> PInRangeTitleLevel;

    // 是否全部是删除段落的情况
    boolean allDel = false;

    // 是否含有表格元素
    boolean hasTable = false;

    // 是否属于没有批注的修订痕迹表格情况
    boolean tableModifyNoComment = false;

    // 是否有段落有Run元素
    boolean hasRun = false;

    // 是否有段落有新增修订
    boolean hasRunIns = false;

    // 是否有段落有删除修订
    boolean hasRunDel = false;

    // 当前所属模块
    long textCurrentModuleId;

    // 当前标题层级信息：一级标题数字，如：一、
    String textTitleStrFirst;

    // 当前标题层级信息：一级标题文本，如：合同当事人
    String textTitleStrContentFirst;

    // 当前标题层级信息：二级标题数字，如（二）
    String textTitleStrSecond;

    // 当前标题层级信息：二级标题文本，如XXX
    String textTitleStrContentSecond;

    // 当前标题层级信息：三级标题数字，如3、
    String textTitleStrThird;

    // 当前标题层级信息：三级标题文本，如XXX
    String textTitleStrContentThird;

    // 当前标题层级信息：四级标题数字，如（4）
    String textTitleStrFourth;

    // 当前标题层级信息：四级标题文本，如XXX
    String textTitleStrContentFourth;
}
