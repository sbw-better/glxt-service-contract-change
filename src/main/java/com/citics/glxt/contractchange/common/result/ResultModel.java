package com.citics.glxt.common.result;

import com.citics.glxt.common.constants.CommonConstants;
import lombok.Data;
import java.io.Serializable;

@Data
public class ResultModel<T> implements Serializable {

    private static final long serialVersionUID = -6104902776087367038L;

    private Integer code;
    private String message;
    private T data = null;

    public ResultModel() {
    }

    public boolean isSuccess() {
        return null != code && 0 == code;
    }

    public static <T> ResultModel<T> setResult(Integer code, String msg, T data) {
        ResultModel resultModel = new ResultModel();
        resultModel.setCode(code);
        resultModel.setData(data);
        resultModel.setMessage(msg);
        return resultModel;
    }

    public static <T> ResultModel<T> success() {
        return setResult(CommonConstants.SUCCESS, CommonConstants.SUCCESSMSG, null);
    }

    public static <T> ResultModel<T> success(T data) {
        return setResult(CommonConstants.SUCCESS, CommonConstants.SUCCESSMSG, data);
    }

    public static <T> ResultModel<T> success(String msg, T data) {
        return setResult(CommonConstants.SUCCESS, msg, data);
    }

    public static <T> ResultModel<T> error() {
        return setResult(CommonConstants.SERVER_FAIL, CommonConstants.FAILMSG, null);
    }

    public static <T> ResultModel<T> error(String msg) {
        return setResult(CommonConstants.SERVER_FAIL, msg, null);
    }

    public static <T> ResultModel<T> error(T data) {
        return setResult(CommonConstants.SERVER_FAIL, CommonConstants.FAILMSG, data);
    }

    public static <T> ResultModel<T> error(String msg, T data) {
        return setResult(CommonConstants.SERVER_FAIL, msg, data);
    }
}

