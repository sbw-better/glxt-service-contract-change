package com.citics.glxt.contractchange.common;

import lombok.Data;
import java.io.Serializable;

/** REST 接口统一响应结构。 */
@Data
public class ResultModel<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    /** HTTP 语义兼容的业务响应码。 */
    private Integer code;
    /** 面向调用方的处理结果说明。 */
    private String message;
    /** 成功数据或结构化失败详情。 */
    private T data;

    /** 创建默认成功响应。 */
    public static <T> ResultModel<T> success(T data) {
        return of(CommonConstants.SUCCESS, "操作成功", data);
    }
    /** 创建默认 500 失败响应。 */
    public static <T> ResultModel<T> error(String message) {
        return of(CommonConstants.FAIL, message, null);
    }
    /** 创建指定响应码、提示和数据的响应。 */
    public static <T> ResultModel<T> of(int code, String message, T data) {
        ResultModel<T> result = new ResultModel<T>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
