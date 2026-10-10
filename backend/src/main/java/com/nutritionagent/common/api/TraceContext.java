package com.nutritionagent.common.api;

import org.slf4j.MDC;

import java.security.SecureRandom;

/**
 * 链路追踪上下文的轻量实现。
 *
 * <p>用 MDC 保存 traceId，使日志格式 {@code %X{traceId}} 可直接输出，
 * 并与 {@link Result#traceId()} 保持一致，便于用户报障时按 ID 定位日志。
 *
 * <p>不引入 Micrometer Tracing / Sleuth：当前是单体应用，跨服务追踪尚无收益，
 * 引入会显著增加依赖与配置复杂度。
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int LENGTH = 16;

    private TraceContext() {
    }

    /** 生成并写入新的 traceId，返回该 ID。 */
    public static String begin() {
        String traceId = newTraceId();
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    /** 读取当前 traceId；不存在时返回 {@code null}，不自动生成以免污染无追踪语义的场景。 */
    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    private static String newTraceId() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = HEX[RANDOM.nextInt(HEX.length)];
        }
        return new String(buf);
    }
}
