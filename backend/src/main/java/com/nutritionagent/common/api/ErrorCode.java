package com.nutritionagent.common.api;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * <p>与 {@code docs/api-contract.md} 第二章错误码表逐条对应，任何一方变更都必须同步。
 * 分段规则：{@code 4xxyy} 客户端问题、{@code 5xxyy} 服务端问题。
 *
 * <p>注意：业务判断一律以本错误码为准，不依赖 HTTP 状态码。HTTP 状态码仅用于网关与
 * 前端拦截器的粗粒度判断。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, 200, "ok"),

    // ===== 4xxxx 客户端问题 =====
    PARAM_INVALID(40001, 400, "参数校验失败"),
    INVALID_WEIGHT(40002, 400, "食用重量不合法"),
    UNAUTHORIZED(40101, 401, "未登录或登录已失效"),
    FORBIDDEN(40301, 403, "无权访问该资源"),
    FOOD_NOT_FOUND(40401, 404, "食品不存在"),
    NUTRITION_NOT_FOUND(40402, 404, "该食品尚未录入营养数据"),
    KNOWLEDGE_NOT_FOUND(40403, 404, "知识库中暂无相关内容"),
    SESSION_NOT_FOUND(40404, 404, "会话不存在"),
    RESOURCE_CONFLICT(40901, 409, "资源冲突"),
    FILE_TOO_LARGE(41301, 413, "图片文件超过大小限制"),
    UNSUPPORTED_FILE_TYPE(41501, 415, "图片格式不支持"),
    RATE_LIMITED(42901, 429, "调用过于频繁，请稍后再试"),

    // ===== 5xxxx 服务端问题（对应 PRD 6.5 的九类异常）=====
    INTERNAL_ERROR(50001, 500, "服务器内部错误"),
    MODEL_TIMEOUT(50301, 503, "AI 服务响应超时，请稍后重试"),
    MODEL_BAD_RESPONSE(50302, 503, "AI 服务返回格式异常"),
    TOOL_FAILED(50303, 503, "查询服务暂时不可用"),
    VISION_UNAVAILABLE(50304, 503, "图片识别服务暂时不可用"),
    OUT_OF_SCOPE(50305, 503, "该问题超出当前系统能力范围"),
    HIGH_RISK_HEALTH(50306, 503, "涉及疾病治疗或高风险饮食，需专业医疗指导");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
