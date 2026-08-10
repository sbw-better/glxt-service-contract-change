package com.citics.glxt.contractchange.common;

/** 可安全返回给接口调用方的预期业务异常。 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 对外响应业务码。 */
    private final int code;

    /** 创建一个 400 业务异常。 */
    public BusinessException(String message) { this(CommonConstants.BAD_REQUEST, message); }

    /** 创建指定响应码的业务异常。 */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
    /** @return 对外响应业务码 */
    public int getCode() { return code; }
}
