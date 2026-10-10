package com.nutritionagent.common.api;

/**
 * 统一响应结构（{@code docs/api-contract.md} 第一章）。
 *
 * <pre>
 * { "code": 0, "message": "ok", "data": {...}, "traceId": "8f3c..." }
 * </pre>
 *
 * <p>接口文档以 {@code docs/api-contract.md} 为唯一权威来源，未引入 springdoc
 * （本机离线仓库中可用版本与 Spring Boot 3.5 不匹配，见 backend/pom.xml 说明）。
 *
 * @param <T> 业务数据类型
 */
public record Result<T>(
        int code,
        String message,
        T data,
        String traceId
) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(),
                data, TraceContext.currentTraceId());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getDefaultMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null, TraceContext.currentTraceId());
    }

    /**
     * 失败但保留 data 载荷。用于「部分成功」语义，例如 AI 服务降级时仍需把
     * 已获取到的结构化营养数据返回给前端（PRD 6.5：非 AI 功能继续可用）。
     */
    public static <T> Result<T> failWithData(ErrorCode errorCode, String message, T data) {
        return new Result<>(errorCode.getCode(), message, data, TraceContext.currentTraceId());
    }

    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}
