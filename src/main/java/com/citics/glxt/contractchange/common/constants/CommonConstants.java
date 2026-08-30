package com.citics.glxt.common.constants;

/** REST 统一响应码常量。 */
public final class CommonConstants {
    /** 请求成功。 */
    public static final int SUCCESS = 200;
    /** 未预期的服务端异常。 */
    public static final int FAIL = 500;
    public final static int SERVER_FAIL = 500;
    /** 请求参数或业务校验失败。 */
    public static final int BAD_REQUEST = 400;
    /** 外部 Embedding 模型暂时不可用。 */
    public static final int SERVICE_UNAVAILABLE = 503;

    public final static String SUCCESSMSG = "操作成功";
    public final static String FAILMSG = "操作失败";

    private CommonConstants() {
    }

    /**
     * 是否
     *  1|是
     *  0|否
     **/
    public static int CONSTANTS_YES = 1;
    public static int CONSTANTS_NO = 0;

    /**
     * 执行结果
     *  1|成功
     *  0|失败
     **/
    public static int EXECUTE_EXECUTE_SUCCESS = 1;
    public static int EXECUTE_EXECUTE_FAIL = 0;

    /**
     * 从FTP文档库下载文件返回类型
     *  1|文件名
     *  2|文件路径
     **/
    public static int FTP_FILE_RETURN_TYPE_FILENAME = 1;
    public static int FTP_FILE_RETURN_TYPE_FILEPATH = 2;

}
