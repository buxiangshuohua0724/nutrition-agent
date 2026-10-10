package com.nutritionagent.common.ratelimit;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.api.TraceContext;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.common.security.GuestKeyResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 接口限流（契约第五章）。
 *
 * <p>为什么落在 {@code operation_log} 上而不是引入 Redis / Bucket4j：本项目的限流粒度是
 * 「登录用户按小时/游客按天」，量级很小；而操作日志本来就要写，用它当计数源可以
 * <b>同时</b>得到「可审计」与「可限流」两个效果，不额外引入基础设施。</p>
 *
 * <p><b>列名必须与 {@code operation_log} 的真实结构一致。</b>该表刻意没有 {@code ip} /
 * {@code user_agent} / {@code status} 这些列：它的注释明确写着「禁止写入敏感信息」，
 * 所以游客限流键落在 {@code client_key}（IP 的单向摘要）上，而不是 IP 明文。</p>
 *
 * <p>时间下界在 Java 侧算好再当参数传入，<b>SQL 里不写数据库特有的日期函数</b>——
 * 生产是 MySQL、开发/测试是 H2，任何方言差异都会变成"测试通过、生产报错"。</p>
 *
 * <p>超限一律返回 42901，且文案不暴露内部配额实现细节（不告诉调用方"第几次、窗口多长"）。</p>
 */
@Component
public class RateLimiter {

    /** 操作日志里的动作标识，同时用作计数维度。 */
    public enum Action {
        RECOGNIZE,
        CHAT
    }

    private final JdbcTemplate jdbc;
    private final GuestKeyResolver guestKeys;

    public RateLimiter(JdbcTemplate jdbc, GuestKeyResolver guestKeys) {
        this.jdbc = jdbc;
        this.guestKeys = guestKeys;
    }

    /**
     * 记录一次操作（用于后续计数）。调用方应在业务真正执行前调用 {@link #check}。
     *
     * @param clientIp 仅当 {@code userId} 为 null（游客）时使用，写入前会先做单向摘要
     */
    public void record(Action action, Long userId, String clientIp, String userAgent,
                       String status, long durationMs) {
        // 登录用户只留 user_id；游客留 client_key。两者都写会让「同一台设备先游客后登录」
        // 的计数被算两遍，配额凭空翻倍。
        String clientKey = userId == null ? guestKeys.of(clientIp) : null;
        jdbc.update("""
                INSERT INTO operation_log (trace_id, user_id, client_key, module, action, success,
                                           message, duration_ms, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TraceContext.currentTraceId(),
                userId,
                clientKey,
                moduleOf(action),
                action.name(),
                "SUCCESS".equalsIgnoreCase(status) ? 1 : 0,
                status,
                (int) durationMs,
                LocalDateTime.now());
    }

    /**
     * 检查配额，超限抛 42901。
     *
     * @param action          动作
     * @param userId          登录用户 id；为 null 视为游客
     * @param clientIp        客户端 IP，游客按它计数（落库前摘要，不存明文）
     * @param windowMinutes   登录用户窗口长度（分钟）
     * @param perWindowLimit  登录用户窗口内允许次数
     * @param guestDailyLimit 游客每日允许次数
     */
    public void check(Action action, Long userId, String clientIp,
                      int windowMinutes, int perWindowLimit, int guestDailyLimit) {
        if (userId == null) {
            LocalDateTime since = LocalDateTime.ofInstant(
                    Instant.now().minusSeconds(24 * 3600L), ZoneId.systemDefault());
            Integer used = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM operation_log
                    WHERE action = ? AND user_id IS NULL AND client_key = ? AND created_at >= ?
                    """, Integer.class, action.name(), guestKeys.of(clientIp), since);
            if (used != null && used >= guestDailyLimit) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(*) FROM operation_log
                WHERE action = ? AND user_id = ? AND created_at >= ?
                """, Integer.class, action.name(), userId, since);
        if (used != null && used >= perWindowLimit) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }

    private static String moduleOf(Action action) {
        return switch (action) {
            case CHAT -> "AGENT";
            case RECOGNIZE -> "RECOGNITION";
        };
    }

    /**
     * 从请求里取客户端标识（游客按 IP 计数）。
     *
     * <p>X-Forwarded-For 是客户端可控头，只采用第一段，且它<b>不作为安全边界</b>——
     * 只影响限流计数，不影响鉴权与数据可见性。
     */
    public static String clientIp(String remoteAddr, String forwardedFor) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = (comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    /** 从请求里取客户端标识；两个 Controller 共用一个口径，避免各处实现漂移。 */
    public static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        return clientIp(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
    }
}
