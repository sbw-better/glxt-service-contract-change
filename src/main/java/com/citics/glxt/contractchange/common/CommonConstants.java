package com.citics.glxt.contractchange.common;

/** REST 统一响应码常量。 */
public final class CommonConstants {
    /** 请求成功。 */
    public static final int SUCCESS = 200;
    /** 未预期的服务端异常。 */
    public static final int FAIL = 500;
    /** 请求参数或业务校验失败。 */
    public static final int BAD_REQUEST = 400;
    /** 外部 Embedding 模型暂时不可用。 */
    public static final int SERVICE_UNAVAILABLE = 503;
    private CommonConstants() {
    }
}
