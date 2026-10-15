package com.citics.glxt.contractchange.docx.service;

import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;

import java.util.ListIterator;
import java.util.Map;

public interface DocxReplaceContent {

    /**
     * 替换目标文件中的内容 主入口
     */
    String replaceContentMain(DocxAnalysisDTO docxAnalysisDTO);

    /**
     * 替换目标文件中的内容
     *
     * @param targetFilePath     目标文件地址
     * @param targetFileSavePath 目标文件保存地址
     * @param wordMLPackage      目标文件的xml信息，若有传不为空，则不需要通过目标文件地址获取
     * @param paramMap           待替换的内容
     * @param paramRangeMap      待替换的内容 范围变量
     * @param isAllText          是否是纯文本替换    true|是;false|否
     * @param isModify           替换时是否开启修订模式    true|是;false|否
     */
    String replaceContent(
            String targetFilePath,
            String targetFileSavePath,
            WordprocessingMLPackage wordMLPackage,
            Map<String, Object> paramMap,
            Map<String, Object> paramRangeMap,
            boolean isAllText,
            boolean isModify
    );

    /**
     * mainIterator 迭代器
     * 处理P元素，替换
     * @paramMap待替换内容，其中例如：CPOC：XXX
     * isAllText 是否是纯文本替换    true|是;false|否
     * isModify 替换时是否开启修订模式    true|是;false|否
     */
    void replaceInP(
            ListIterator<Object> mainIterator,
            P paragraph,
            Map<String, Object> paramMap,
            boolean isAllText,
            boolean isModify
    );
}
