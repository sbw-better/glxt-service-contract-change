package com.citics.glxt.contractchange.docx.service;

import com.citics.glxt.contractchange.docx.domain.ModifyConfigTableDetail;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;

import java.util.List;
import java.util.ListIterator;
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

    void saveModifiedDocument(WordprocessingMLPackage wordMLPackage, String fileSavePath) throws Docx4JException;

    int chinesNumToInt(String chnNumber);

    String intToChinesNum(int number);

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

    /**
     * 提取段落的Run格式（RPr）。取第一个非空Run或者RunIns的格式
     */
    RPr getFirstRunPr(P paragraph);

    /**
     * 按照换行符拆分段落，适用于多个带序号前缀段落实际上都在同一个P中的情况
     */
    List<P> splitParagraphByLineBreaks(P originalP);

    /**
     * 用原始节点集合 创建段落，1:1还原结构，保留修订/批注/格式
     * 创建与原段落样式完全一致的新段落（包含PPr段落格式+RPr字符格式）
     *
     * @param pPr 原段落格式（继承格式）
     * @param contentList 新段落各类节点集合内容
     * @return 格式一致的新段落
     */
    P createParagraphWithSameNodes(PPr pPr, List<Object> contentList);

    /**
     * 按 <w:cr/> <w:br/> 拆分P，并替换原来的P，适用于多个带序号前缀段落实质上都在同一个P中的情况
     */
    void splitAndReplaceParagraph(
            ListIterator<Object> mainIterator,
            List<List<Object>> nodeList,
            PPr ppr,
            RPr rpr,
            boolean goOnIterate
    );

    /**
     * 按 <w:cr/> <w:br/> 拆分P，并替换原来的P，适用于多个带序号前缀段落实质上都在同一个P中的情况
     */
    void splitAndReplaceParagraph(ListIterator<Object> mainIterator, List<P> pList, PPr ppr, boolean goOnIterate);

    /**
     * 新增段落：插入到当前段落前/后（维护迭代器位置）
     */
    void addNewParagraph(ListIterator<Object> iterator, P newPara, boolean isAfter);

    /**
     * 递归检查对象是否含R元素
     */
    boolean hasRun(List<?> nodeList);

    /**
     * 递归检查对象是否含修订（新增）
     */
    boolean hasRunIns(List<?> nodeList);

    /**
     * 递归检查对象是否含修订（删除）
     */
    boolean hasRunDel(List<?> nodeList);

    /**
     * 递归检查对象是否含CommentRangeStart元素
     */
    boolean hasCommentStart(List<?> nodeList);

    /**
     * 递归检查对象是否含CommentRangeEnd元素
     */
    boolean hasCommentEnd(List<?> nodeList);

    /**
     * 提取段落的完整文本内容
     */
    String extractFullParagraphText(List<?> paragraphContent);

    /**
     * 提取段落的完整文本内容（修订模式中修改前的文本）
     */
    String extractFullParagraphTextBeforeModify(List<?> paragraphContent);

    /**
     * 递归提取节点列表中的文本内容
     */
    void extractTextFromNodeList(List<?> nodeList, StringBuilder fullText);

    /**
     * 递归提取节点列表中的文本内容（修订模式中修改前的文本）
     */
    public void extractTextFromNodeListBeforeModify(List<?> nodeList, StringBuilder fullText);

    /**
     * 获取findCommentRangeStart或CommentRangeEnd元素的ID
     */
    List<String> getCommentId(List<?> nodeList, int type);

    /**
     * 是否有R$CommentReference元素，若有返回批注ID，若无则返回""
     */
    void hasCommentReference(List<?> nodeList, String res);

    /**
     * 递归查找节点及其下面结构的所有Tbl表格对象
     *
     * @param node 当前遍历节点（可能是Tbl，也可能是某个子节点）
     * @param tblList 收集到的表格列表（输出参数）
     */
    void findAllTblInNode(Object node, List<Tbl> tblList);

    /**
     * 创建与原段落格式完全一致的新段落（包含PPr段落格式+RPr字符格式）
     *
     * @param sourcePara 源段落（继承格式）
     * @param content 新段落内容
     * @param modify 是否采用修订模式
     * @return 格式一致的新段落
     */
    P createParagraphWithSameStyle(P sourcePara, String content, boolean modify);

    /**
     * 创建与原段落格式完全一致的新段落（包含PPr段落格式+RPr字符格式）
     *
     * @param ppr 段落格式
     * @param rpr 文本格式
     * @param content 新段落内容
     * @return 格式一致的新段落
     */
    P createParagraphWithSameStyle(PPr ppr, RPr rpr, String content);

    /**
     * 判断一个批注是不是【空批注】（无任何文本内容）
     */
    boolean isEmptyComment(Comments.Comment comment);

    /**
     * 创建普通加粗Run（无修订，包含JAXBElement包装的Text）
     *
     * @param text 文本内容
     * @param rPr 格式
     * @return 新生成的R元素
     */
    R createNormalRunBold(String text, RPr rPr);

    /**
     * 提取段落的所有元素，按照换行符分割，适用于多个带序号前缀段落实质上都在同一个P中的情况
     */
    List<List<Object>> extractFullParagraphNodeList(List<?> paragraphContent);

    /**
     * 段落中替换关键字（保留格式）
     *
     * @param paragraph 目标段落
     * @param pgText 目标段落文本
     * @param keyword 关键字
     * @param replaceContent 替换的关键字
     * @param modify 是否开启修订模式
     */
    void replaceKeyWordInPara(P paragraph, String pgText, String keyword, String replaceContent, boolean modify);

    /**
     * 创建RunDel
     *
     * @param r R元素
     * @return 新生成的RunDel元素
     */
    RunDel createNormalRunDel(R r);

    /**
     * 递归检查对象是否含分页元素
     */
    boolean hasPage(List<?> nodeList);

    /**
     * 替换段落文本（带修订模式，先删后增）
     *
     * @param paragraph 目标段落
     * @param oldText 旧文本
     * @param newText 新文本
     * @param rpr 格式
     * @param modify 是否带修订
     */
    void replaceParagraphText(P paragraph, String oldText, String newText, RPr rpr, boolean modify);

    // 校验文本的每层标题和配置记录的每层标题是否匹配
    boolean isTitleMatched(Map<Integer, String> textTitleMap,
                           Map<Integer, String> configTitleMap,
                           int matchLevel);

    /**
     * 在关键字前后追加文本（整体替换段落内容，保留格式）
     * @param paragraph 目标段落
     * @param originalText 段落原有完整文本
     * @param keyword 关键字
     * @param appendText 要追加的文本
     * @param isBefore true=关键字前追加，false=关键字后追加
     */
    void appendTextToPara(P paragraph, String originalText, String keyword, String appendText, boolean isBefore);

    /**
     * 生成新段落的带序号内容
     */
    String generateNewParagraphContent(String content,
                                       Map<Integer, String> textTitleMap,
                                       boolean generateNext);

    /**
     * 按层级+数字生成标题前缀
     * @param level 标题层级（1-5）
     * @param num 序号数字（如1、2、3）
     * @return 对应格式的标题前缀（如：5级+2 -> "2）"）
     */
    String generateTitlePrefixByLevelAndNum(int level, int num);

    /**
     * 删除段落中替换关键字（保留格式）：删除多个
     * @param paragraph 目标段落
     * @param pgText 目标段落文本
     * @param keywords 关键字列表
     * @param modify 是否开启修订模式
     */
    void deleteKeyWordInParaBatch(P paragraph,
                                  String pgText,
                                  List<String> keywords,
                                  boolean modify);

    /**
     * 删除段落中替换关键字（保留格式）：删除单个
     * @param paragraph 目标段落
     * @param pgText 目标段落文本
     * @param keyword 关键字
     * @param modify 是否开启修订模式
     */
    void deleteKeyWordInParaOne(P paragraph, String pgText, String keyword, boolean modify);

    /**
     * 提取表格行的完整文本内容
     * @param tr 表格行对象
     * @return 行文本
     */
    String extractTableRowText(Tr tr);

    /**
     * 提取单个表格单元格（Tc）的文本内容（递归解析P->R->Text层级）
     * @param tc 单元格对象
     * @return 单元格文本（空则返回""）
     */
    String extractTableCellText(Tc tc);

    /**
     * 在指定表格行下方插入N行新数据
     * @param table 目标表格
     * @param targetRow 目标行（匹配关键字的行）
     * @param targetRowIndex 目标行在目标表格中的索引
     * @param insertRowNum 待插入行数
     */
    void insertRowsBelow(Tbl table, Tr targetRow, int targetRowIndex, long insertRowNum,
                         List<ModifyConfigTableDetail> tableDetailList);

    /**
     * 复制段落格式（对齐方式、行距、缩进等）
     * @param sourceP 源段落（目标行的段落）
     * @param targetP 目标段落（新行的段落）
     */
    void copyParagraphFormat(P sourceP, P targetP);

    /**
     * 仅替换段落的序号前缀（如（2）→（3）），仅序号变化时为序号加修订标记，保留正文和原有修订格式
     * @param paragraph 目标段落
     * @param paraText 目标段落文本内容
     * @param oldPrefix 旧序号前缀（如"（2）"）
     * @param newPrefix 新序号前缀（如"（3）"）
     * @param newPara 是否是新插入的新段落
     */
    void replaceTitlePrefixOnly(P paragraph, String paraText, String oldPrefix, String newPrefix, boolean newPara);

    /**
     * 从源单元格提取第一个有效文本运行对象（用于继承字体格式）
     * @param sourceTc 源单元格
     * @return 第一个包含文本的R对象，无则返回null
     */
    RPr getFirstRunPrTc(Tc sourceTc);

    /**
     * 复制单元格格式（宽度、边距、对齐、边框等）
     * @param sourceTc 源单元格（目标行的单元格）
     * @param targetTc 目标单元格（新行的单元格）
     */
    void copyTableCellFormat(Tc sourceTc, Tc targetTc);

    /**
     * 段落中替换关键字（保留格式） 可能是文本、P、表格替换
     * @param paragraph 目标段落
     * @param pgText 目标段落文本
     * @param keyword 关键字
     * @param replaceContent 待替换的内容列表
     * @param modify 是否开启修订模式
     */
    void replaceKeyWordInParaAnything(ListIterator<Object> mainIterator,
                                      P paragraph,
                                      String pgText,
                                      String keyword,
                                      List<Object> replaceContent,
                                      boolean modify);
}
