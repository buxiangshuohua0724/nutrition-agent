package com.nutritionagent.common.exception;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.common.api.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>统一把异常转换为 {@link Result}，保证任何出口的响应结构一致。
 * 这里刻意把「框架层异常」也映射到项目自己的错误码，避免前端要同时处理两套编码体系。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：预期内的失败，按 WARN 记录，不打印堆栈以免污染日志。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.warn("业务异常 code={} path={} msg={}", code.getCode(), request.getRequestURI(), ex.getMessage());
        return withStatus(code, ex.getMessage());
    }

    /** @Valid 校验失败（请求体）。把所有字段错误聚合返回，避免前端逐个试错。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", detail);
        return withStatus(ErrorCode.PARAM_INVALID, "参数校验失败：" + detail);
    }

    /** @Valid 校验失败（表单/查询参数绑定）。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败：{}", detail);
        return withStatus(ErrorCode.PARAM_INVALID, "参数校验失败：" + detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return withStatus(ErrorCode.PARAM_INVALID, "缺少必填参数：" + ex.getParameterName());
    }

    /** 请求体无法解析，通常是 JSON 格式错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败：{}", ex.getMessage());
        return withStatus(ErrorCode.PARAM_INVALID, "请求体格式不正确，无法解析 JSON");
    }

    /** 文件超限：由 multipart 配置在进入 Controller 前触发，必须映射到 41301。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超过大小限制：{}", ex.getMessage());
        return withStatus(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage() + "（单张不超过 10MB）");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<Void> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return withStatus(ErrorCode.UNSUPPORTED_FILE_TYPE,
                "不支持的内容类型：" + ex.getContentType());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return withStatus(ErrorCode.PARAM_INVALID, "请求方法不支持：" + ex.getMethod());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandler(NoHandlerFoundException ex) {
        return withStatus(ErrorCode.FOOD_NOT_FOUND, "接口不存在：" + ex.getRequestURL());
    }

    /**
     * 兜底。
     *
     * <p>对外只返回统一话术，绝不把异常堆栈或 SQL 片段透给客户端（PRD 12.4 安全要求）；
     * 真正的排查依据是日志里的 traceId。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = TraceContext.currentTraceId();
        log.error("未预期异常 traceId={} path={}", traceId, request.getRequestURI(), ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage() + "，请稍后重试（追踪号 " + traceId + "）");
    }

    private String describeFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    /**
     * 业务异常需要同时设置 HTTP 状态码与业务码。
     *
     * <p>因为 {@code Result} 是 record 且由 {@code @RestControllerAdvice} 直接序列化，
     * 这里通过 {@link org.springframework.web.context.request.RequestContextHolder}
     * 拿不到响应对象，故改用 {@code ResponseEntity} 会造成两个入口结构不一致。
     * 折中方案：HTTP 状态统一 200，业务判断一律以 {@code code} 为准（契约已如此约定），
     * 但对 401/403/429/413 这类需要网关或浏览器原生行为介入的状态，额外在
     * {@code ErrorCode.httpStatus} 中保留，由 {@code ApiStatusFilter} 落成真实状态码。
     */
    private Result<Void> withStatus(ErrorCode code, String message) {
        return Result.fail(code, message);
    }
}
