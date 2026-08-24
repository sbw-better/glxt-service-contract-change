package com.citics.glxt.contractchange.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

/** 将异常转换为统一响应，并为运维保留必要且不含合同正文的诊断日志。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** 处理可预期业务异常，不输出堆栈。 */
    @ExceptionHandler(ContractChangeBusinessException.class)
    public ContractChangeResult<Void> handleBusiness(ContractChangeBusinessException ex) {
        log.warn("业务处理失败, code={}, message={}", ex.getCode(), ex.getMessage());
        return ContractChangeResult.of(ex.getCode(), ex.getMessage(), null);
    }

    /** 处理 Bean Validation 参数校验异常。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ContractChangeResult<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() == null
                ? "请求参数错误" : ex.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("请求参数校验失败, message={}", message);
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, message, null);
    }

    /** 处理缺少模型平台审计所需 user_id 请求头的情况。 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ContractChangeResult<Void> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = ex.getHeaderName() + "请求头不能为空";
        log.warn("请求头缺失, header={}", ex.getHeaderName());
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, message, null);
    }

    /** 处理 Controller 方法参数上的 Bean Validation 校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ContractChangeResult<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().isEmpty()
                ? "请求参数错误" : ex.getConstraintViolations().iterator().next().getMessage();
        log.warn("请求参数约束校验失败, message={}", message);
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, message, null);
    }

    /** 处理未预期异常：记录堆栈，但不向调用方泄露内部细节。 */
    @ExceptionHandler(Exception.class)
    public ContractChangeResult<Void> handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return ContractChangeResult.error("系统处理失败，请联系管理员");
    }
}
