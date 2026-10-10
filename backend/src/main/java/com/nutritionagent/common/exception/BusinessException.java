package com.nutritionagent.common.exception;

import com.nutritionagent.common.api.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>所有可预期的失败都应抛出本异常并携带 {@link ErrorCode}，
 * 由 {@code GlobalExceptionHandler} 统一转换为 {@code Result}。
 * 禁止在 Controller 里手写 try/catch 拼装错误响应，否则错误码会失去一致性。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    // ===== 高频场景的语义化工厂方法，避免各处重复写错误码 =====

    public static BusinessException paramInvalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }

    public static BusinessException foodNotFound(Object id) {
        return new BusinessException(ErrorCode.FOOD_NOT_FOUND, "食品不存在：" + id);
    }

    public static BusinessException nutritionNotFound(String detail) {
        return new BusinessException(ErrorCode.NUTRITION_NOT_FOUND, detail);
    }

    public static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    /**
     * 越权访问统一入口。
     *
     * <p>刻意不区分「记录不存在」与「记录属于他人」，统一返回 40301，
     * 避免通过响应差异探测他人资源是否存在（PRD 12.4）。
     */
    public static BusinessException noPermissionForResource() {
        return new BusinessException(ErrorCode.FORBIDDEN, "无权访问该资源");
    }
}
