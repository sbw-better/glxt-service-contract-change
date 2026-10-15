package com.citics.glxt.contractchange.docx.domain.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.Tbl;

import java.util.Map;

@Data
@NoArgsConstructor
public class EnhanceObj {

    // 类型：1|段落；2|表格
    int type;

    // 属于段落时：段落
    P p;

    // 属于段落时：段落格式
    PPr ppr;

    // 属于段落时：段落文本（当前）
    String pText;

    // 属于段落时：段落文本（原始）
    String pTextBefore;

    // 属于段落时：当前段落是否是标题
    boolean isTitle = false;

    // 属于段落时：当前段落是标题时(isTitle=true)的标题级别
    Integer titleLevel;

    // 属于段落时：当前段落是标题时(isTitle=true)的标题文本前缀，例如：一、 （二） 1、
    String titleStr;

    // 属于表格时：旧表格
    Tbl tableOld;

    // 属于表格时：新表格（当前表格）
    Tbl tableNew;

    // 是否有Run元素
    boolean hasRun = false;

    // 是否有新增修订
    boolean hasRunIns = false;

    // 是否有删除修订
    boolean hasRunDel = false;

    // 是否有批注起始元素
    boolean hasCommentStart = false;

    // 是否有批注结束元素
    boolean hasCommentEnd = false;

    // 若含批注时批注ID
    String commentId;

    // 当前所属模块
    long textCurrentModuleId;

    // 当前标题层级信息，存标题前缀，例如：key:1,val:一、
    Map<Integer, String> textCurrentTitleMapNum;

    // 当前标题层级信息，存标题文本，例如：key:1,val:合同当事人
    Map<Integer, String> textCurrentTitleMapText;
}
