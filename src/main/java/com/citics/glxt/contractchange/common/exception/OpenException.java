package com.citics.glxt.common.exception;

import com.citics.glxt.common.constants.ErrorCode;

public class OpenException extends RuntimeException {

    private static final long serialVersionUID = 470956773140545531L;

    private int code = ErrorCode.ERROR.getCode();

    public OpenException() {

    }

    public OpenException(String msg) {
        super(msg);
    }

    public OpenException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public OpenException(int code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

}

