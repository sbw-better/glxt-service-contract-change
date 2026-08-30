package com.citics.glxt.contractchange.docx.service;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;

import java.util.List;
import java.util.Map;

/**
 * docx解析通用方法
 */
public interface DocxCommonService {

    /**
     * 加载文件
     * @param filePath
     * @return
     */
    WordprocessingMLPackage getXmlDocument(String filePath);

    /**
     * 提取段落的Run格式（RPr）：取第一个非空Run或者RunIns的格式
     * @param paragraph
     * @return
     */
    RPr getFirstRunPrP(P paragraph);

    /**
     * 提取段落的完整文本内容，适用于多个带序号前缀的段落实际上都在同一个P中的情况
     * @param paragraphContent
     * @return
     */
    List<String> extractFullParagraphTextList(List<?> paragraphContent);

    /**
     * 获取文本所属模块
     * @param pgText
     * @param textCurrentModuleId
     * @return
     */
    long getCurrentModule(String pgText, long textCurrentModuleId);

    /**
     * 更新当前文本的标题层级Map
     * @param pgText
     */
    void updateTextTitleLevelMap(String pgText, Map<Integer, String> mapNum, Map<Integer, String> mapText);

    /**
     * 处理自带的标题序号前缀，返回存文本的新段落内容
     * @param paragraph
     * @param ppr
     * @param rpr
     * @param pgText
     * @param titleModeMap
     * @param textCurrentTitleMapNum
     * @param b
     * @return
     */
    String handleTitleMode(P paragraph, PPr ppr, RPr rpr, String pgText, Map<String, String> titleModeMap, Map<Integer, String> textCurrentTitleMapNum, boolean b);

    /**
     * 获取当前最低级别的标题
     * @param textCurrentTitleMapNum
     * @return
     */
    int getCurrentValidTitleLevel(Map<Integer, String> textCurrentTitleMapNum);

    /**
     * 按指定层级，从段落文本中直接正则匹配提取前缀
     * @param pgText
     * @param titleLevel
     * @return
     */
    String extractPrefixByLevel(String pgText, int titleLevel);

    /**
     * 获取段落的标题格式
     * @param numbering
     * @param ppr
     * @return
     */
    Map<String, String> getTitleModeFromP(Numbering numbering, PPr ppr);

    void extractTextFromNodeList2(List<?> nodeList, StringBuilder fullText, List<String> textList);

    void insertTitleBeforeParagraph(P paragraph, String pgText, String newPrefix, RPr rpr, boolean modify);

    String generateNextTitlePrefix(String currentPrefix);

    R createNormalRun(String text, RPr rPr, boolean isDel);

    RunIns createNormalRunIns(R r);

    int chineseNumToInt(String chnNumber);

    String intToChineseNum(int number);
}
