package com.citics.glxt.common.handler;

import com.citics.glxt.common.result.ContractChangeResult;
import com.citics.glxt.common.constants.CommonConstants;
import com.citics.glxt.common.exception.ContractChangeBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    /** 处理缺少模型平台审计所需 UserId 请求头的情况。 */
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

    /** 处理缺少multipart文件字段的请求。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ContractChangeResult<Void> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("上传请求缺少文件字段, part={}", ex.getRequestPartName());
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST,
                ex.getRequestPartName() + "文件不能为空", null);
    }

    /** 处理超过Spring上传大小限制的Excel文件。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ContractChangeResult<Void> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超过大小限制, maxUploadSize={}", ex.getMaxUploadSize());
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, "Excel文件不能超过20MB", null);
    }

    /** 处理无法解析的JSON请求体，不记录可能包含合同正文的异常内容。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ContractChangeResult<Void> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("请求JSON格式不正确, exception={}", ex.getClass().getSimpleName());
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, "请求JSON格式不正确", null);
    }

    /** 处理错误的Content-Type。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ContractChangeResult<Void> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("请求Content-Type不支持, contentType={}", ex.getContentType());
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, "请求Content-Type不支持", null);
    }

    /** 处理损坏或格式不完整的multipart请求。 */
    @ExceptionHandler(MultipartException.class)
    public ContractChangeResult<Void> handleMultipart(MultipartException ex) {
        log.warn("multipart上传请求格式不正确, exception={}", ex.getClass().getSimpleName());
        return ContractChangeResult.of(CommonConstants.BAD_REQUEST, "上传请求格式不正确", null);
    }

    /** 处理未预期异常：记录堆栈，但不向调用方泄露内部细节。 */
    @ExceptionHandler(Exception.class)
    public ContractChangeResult<Void> handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return ContractChangeResult.error("系统处理失败，请联系管理员");
    }
}
