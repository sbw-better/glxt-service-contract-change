package com.citics.glxt.contractchange.docx.domain.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;

import java.util.Map;

@Data
@NoArgsConstructor
public class EnhanceP {

    // 段落
    P p;

    // 段落格式
    PPr ppr;

    // 段落文本（当前）
    String pText;

    // 段落文本（原始）
    String pTextBefore;

    // 段落是否有Run元素
    boolean hasRun;

    // 段落是否有新增修订
    boolean hasRunIns;

    // 段落是否有删除修订
    boolean hasRunDel;

    // 段落是否有批注起始元素
    boolean hasCommentStart;

    // 段落是否有批注结束元素
    boolean hasCommentEnd;

    // 段落若含批注时批注ID
    String commentId;

    // 当前所属模块
    long textCurrentModuleId;

    // 当前标题层级信息，存标题前缀，例如：一、
    Map<Integer, String> textCurrentTitleMapNum;

    // 当前标题层级信息，存标题文本，例如：合同当事人
    Map<Integer, String> textCurrentTitleMapText;

    // 当前段落是否是标题
    boolean isTitle;

    // 当前段落是标题时(isTitle=true)的标题级别
    Integer titleLevel;

    // 当前段落是标题时(isTitle=true)的标题文本前缀，例如：一、 （一） 1、
    String titleStr;
}
