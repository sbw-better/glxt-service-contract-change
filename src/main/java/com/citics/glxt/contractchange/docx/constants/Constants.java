package com.citics.glxt.contractchange.docx.constants;

public class Constants {

    /**
     * 基金合同版本号字符串
     */
    public static String CONTRACT_VERSION_CODE_STRING_KEYWORD = "基金合同";

    /**
     * 解析操作类型字典值
     *  1|根据修订记录生成补充协议（generateSupplementByRevision）
     *  2|根据补充协议生成合同
     *  3|根据协商函整改生成新版合同（analyzeAndGenerate）
     *  4|模板变量和作用域变量替换（replace）
     **/
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
     **/
    public static long CONTRACT_ANALYSIS_MODULE_OTHER = -99;
    public static long CONTRACT_ANALYSIS_MODULE_INIT = -999;
    public static long CONTRACT_ANALYSIS_MODULE_COVER = 1;
    public static long CONTRACT_ANALYSIS_MODULE_SPECIAL = 2;
    public static long CONTRACT_ANALYSIS_MODULE_RISK = 3;
    public static long CONTRACT_ANALYSIS_MODULE_UNDERTAKING = 4;
    public static long CONTRACT_ANALYSIS_MODULE_CONTENTS= 5;
    public static long CONTRACT_ANALYSIS_MODULE_MAIN = 6;
    public static long CONTRACT_ANALYSIS_MODULE_SIGNATURE = 7;
    public static long CONTRACT_ANALYSIS_MODULE_ATTACHMENT = 8;

    /**
     * word自带标题前缀
     **/
    public static String TITLE_MODE = "MODE";
    public static String TITLE_NUMFMT = "NUMFMT";

    /**
     * 段落配置内容换行标志
     **/
    public static String PARAGRAPH_SPLIT_FLAG = "&&next&&";

    /**
     * 标题层级最大值
     */
    public static int MAX_TITLE_LEVEL = 5;

    /**
     * 1|CommentRangeStart元素类型
     * 2|CommentRangeEnd元素类型
     **/
    public static int COMMENT_RANGE_START = 1;
    public static int COMMENT_RANGE_END = 2;

}
