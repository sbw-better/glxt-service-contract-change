package com.citics.glxt.contractchange.common;

import lombok.Data;

import java.io.Serializable;

/** 合同段落变更类型识别接口的统一响应结构，使用业务专属名称避免集成时类名冲突。 */
@Data
public class ContractChangeResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** HTTP 语义兼容的业务响应码。 */
    private Integer code;
    /** 面向调用方的处理结果说明。 */
    private String message;
    /** 成功数据或结构化失败详情。 */
    private T data;

    /** 创建默认成功响应。 */
    public static <T> ContractChangeResult<T> success(T data) {
        return of(CommonConstants.SUCCESS, "操作成功", data);
    }

    /** 创建默认 500 失败响应。 */
    public static <T> ContractChangeResult<T> error(String message) {
        return of(CommonConstants.FAIL, message, null);
    }

    /** 创建指定响应码、提示和数据的响应。 */
    public static <T> ContractChangeResult<T> of(int code, String message, T data) {
        ContractChangeResult<T> result = new ContractChangeResult<T>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
