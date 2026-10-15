package com.citics.glxt.contractchange.docx.constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 *
 * </p>
 *
 * @author wuxingkun
 * @since 2026-01-22
 */
public class Constants {

    /**
     * 基金合同版本号字符串
     */
    public static String CONTRACT_VERSION_CODE_STRING_KEYWORD = "基金合同";

    /**
     * 解析操作类型字段值
     * 1|根据修订记录生成补充协议（generateSupplementByRevision）
     * 2|根据补充协议生成合同
     * 3|根据协商调整改生成新版合同（analyzeAndGenerate）
     * 4|模板变量和作用域变量替换（replace）
     */
    public static long OPERATION_GUIDANCE_GENERATE_CONDITION_ONE = 1;
    public static long OPERATION_GUIDANCE_GENERATE_CONDITION_TWO = 2;
    public static long OPERATION_GUIDANCE_GENERATE_CONDITION_THREE = 3;
    public static long OPERATION_GUIDANCE_GENERATE_CONDITION_FOUR = 4;

    /**
     * 合同解析模块字典值
     * 1|合同封面
     * 2|特别约定
     * 3|风险揭示书
     * 4|合格投资者承诺书
     * 5|目录
     * 6|正文
     * 7|签署页
     * 8|附件
     * -99|其它
     * -999|初始化值
     */
    public static long CONTRACT_ANALYSIS_MODULE_OTHER = -99;
    public static long CONTRACT_ANALYSIS_MODULE_INIT = -999;
    public static long CONTRACT_ANALYSIS_MODULE_COVER = 1;
    public static long CONTRACT_ANALYSIS_MODULE_SPECIAL = 2;
    public static long CONTRACT_ANALYSIS_MODULE_RISK = 3;
    public static long CONTRACT_ANALYSIS_MODULE_UNDERTAKING = 4;
    public static long CONTRACT_ANALYSIS_MODULE_CONTENTS = 5;
    public static long CONTRACT_ANALYSIS_MODULE_MAIN = 6;
    public static long CONTRACT_ANALYSIS_MODULE_SIGNATURE = 7;
    public static long CONTRACT_ANALYSIS_MODULE_ATTACHMENT = 8;

    public static final Map<Long, String> MODULE_NAME_MAP;
    static {
        MODULE_NAME_MAP = new HashMap<>();
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_OTHER, "其他");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_INIT, "初始化模块");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_COVER, "合同封面");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_SPECIAL, "特别约定");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_RISK, "风险揭示书");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_UNDERTAKING, "合格投资者承诺书");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_CONTENTS, "目录");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_MAIN, "");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_SIGNATURE, "签署页");
        MODULE_NAME_MAP.put(CONTRACT_ANALYSIS_MODULE_ATTACHMENT, "附件");
    }

    /**
     * 修改方式分类：
     * 1|关键字前-文本-段落内文本
     * 50|关键字前-文本-段落-无序号变化
     * 100|关键字前-文本-段落-有序号变化
     * 150|关键字后-文本-段落内文本
     * 200|关键字后-文本-段落-无序号变化
     * 250|关键字后-文本-段落-有序号变化
     * 300|表格行关键字前-表格行
     * 350|表格行关键字后-表格行
     * 400|标题下最后位置-段落-无序号变化
     * 450|标题下最后位置-段落-有序号变化
     */
    public static long MODIFY_TYPE_TEXT_BEFORE_KEYWORDS_IN = 1;
    public static long MODIFY_TYPE_TEXT_BEFORE_KEYWORDS_OUT_NONECHANGE = 50;
    public static long MODIFY_TYPE_TEXT_BEFORE_KEYWORDS_OUT_CHANGE = 100;
    public static long MODIFY_TYPE_TEXT_BACK_KEYWORDS_IN = 150;
    public static long MODIFY_TYPE_TEXT_BACK_KEYWORDS_OUT_NONECHANGE = 200;
    public static long MODIFY_TYPE_TEXT_BACK_KEYWORDS_OUT_CHANGE = 250;
    public static long MODIFY_TYPE_TABLE_BEFORE_KEYWORDS = 300;
    public static long MODIFY_TYPE_TABLE_BACK_KEYWORDS = 350;
    public static long MODIFY_TYPE_TEXT_LAST_NONECHANGE = 400;
    public static long MODIFY_TYPE_TEXT_LAST_CHANGE = 450;

    /**
     * 定位方式
     * 1|关键字
     * 2|章节位置
     */
    public static long LOCATION_TYPE_KEYWORD = 1;
    public static long LOCATION_TYPE_TITLE = 2;

    /**
     * 关键字变更类型
     * 1|新增
     * 2|删除
     * 3|修改
     */
    public static long KEYWORDS_MODIFY_TYPE_ADD = 1;
    public static long KEYWORDS_MODIFY_TYPE_DELETE = 2;
    public static long KEYWORDS_MODIFY_TYPE_UPDATE = 3;

    /**
     * 是否
     * 1|是
     * 0|否
     */
    public static int CONSTANTS_YES = 1;
    public static int CONSTANTS_NO = 0;

    /**
     * 执行结果
     * 1|成功
     * 0|失败
     */
    public static int EXECUTE_EXECUTE_SUCCESS = 1;
    public static int EXECUTE_EXECUTE_FAIL = 0;

    /**
     * 从FTP文档库下载文件返回类型
     * 1|文件名
     * 2|文件路径
     */
    public static int FTP_FILE_RETURN_TYPE_FILENAME = 1;
    public static int FTP_FILE_RETURN_TYPE_FILEPATH = 2;

    /**
     * 对象名称
     */
    public static String STRING_OBJECT_MLPACKAGE = "WordprocessingMLPackage";
    public static String STRING_OBJECT_WDK = "TpifCpwdk";

    /**
     * word自带标题前缀
     */
    public static String TITLE_MODE = "MODE";
    public static String TITLE_NUMFMT = "NUMFMT";

    /**
     * 段落配置内容换行标志
     */
    public static String PARAGRAPH_SPLIT_FLAG = "&&next&&";

    /**
     * 标题层级最大值
     */
    public static int MAX_TITLE_LEVEL = 5;

    /**
     * 1|CommentRangeStart元素类型
     * 2|CommentRangeEnd元素类型
     */
    public static int COMMENT_RANGE_START = 1;
    public static int COMMENT_RANGE_END = 2;

    /**
     * 补充协议模板存替换内容变量
     */
    public static String AGREEMENT_PLACEHOLDERS = "#{BCXYMBXGNR}";

    /**
     * 经办人操作类型
     * 1|不处理
     * 2|删除
     * 3|新增
     */
    public static Long JBR_HANDLE_TYPE_NO = 1L;
    public static Long JBR_HANDLE_TYPE_DEL = 2L;
    public static Long JBR_HANDLE_TYPE_ADD = 3L;
}
