package com.nutritionagent.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritionagent.agent.dto.AgentAnswer;
import com.nutritionagent.agent.llm.LanguageModel;
import com.nutritionagent.agent.tool.AgentToolkit;
import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.db.GeneratedKeys;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.common.ratelimit.RateLimiter;
import com.nutritionagent.common.security.GuestKeyResolver;
import com.nutritionagent.common.security.UserPrincipal;
import com.nutritionagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 问答（FR-005）。
 *
 * <p>本服务是<b>协调层</b>（PRD 第六章）：它不查营养数值、不算热量、不检索知识，
 * 这些全部委托给 {@link AgentToolkit} 背后的业务服务；它只负责会话管理、限流、落库，
 * 以及把工具层取到的事实交给 {@link LanguageModel} 组织成文本。
 *
 * <p>{@code degraded} 的语义（契约 4.3）：模型不可用时业务码仍然是 0（请求成功了，
 * 用户确实拿到了基于结构化数据的回答），但在回答里把"这条回答不是模型生成的"如实标出。
 * 这与 PRD 6.5 的要求一致——外部模型不可用不应该让用户什么都拿不到。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** 登录用户 30 次/分钟（契约第五章）。 */
    private static final int WINDOW_MINUTES = 1;
    private static final int PER_WINDOW_LIMIT = 30;

    private static final int MAX_QUESTION_LENGTH = 500;

    /**
     * 哪些不确定性算「降级」。
     *
     * <p>区分很重要：「没找到你问的那个食品」是一个正常的回答（用户能理解也没被误导），
     * 不算降级；而「这个食品有营养项在库里就是缺失的」意味着用户拿到的信息不完整，
     * 必须显式标成降级，前端要提示。
     */
    private static final java.util.Set<String> DEGRADING_CODES = java.util.Set.of(
            "DATA_MISSING", "NUTRITION_NOT_FOUND", "CALCULATION_FAILED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AgentToolkit toolkit;
    private final LanguageModel languageModel;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final GuestKeyResolver guestKeys;

    public AgentService(JdbcTemplate jdbc,
                        ObjectMapper objectMapper,
                        AgentToolkit toolkit,
                        LanguageModel languageModel,
                        RateLimiter rateLimiter,
                        AppProperties properties,
                        GuestKeyResolver guestKeys) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.toolkit = toolkit;
        this.languageModel = languageModel;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.guestKeys = guestKeys;
    }

    /** 问答结果。{@code degraded} 为真时前端必须提示"当前为降级回答"。 */
    public record ChatOutcome(AgentAnswer answer, String sessionTitle, Long sessionId) {
    }

    // ------------------------------------------------------------------ 问答

    public ChatOutcome chat(Long sessionId, String question, Long foodId, Long variantId, BigDecimal weight,
                            UserPrincipal principal, String clientIp, String userAgent) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请输入要咨询的问题");
        }
        String q = question.trim();
        if (q.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "问题过长，请控制在 " + MAX_QUESTION_LENGTH + " 字以内");
        }

        Long userId = principal.isLoggedIn() ? principal.userId() : null;
        String guestKey = userId == null ? guestKeyOf(clientIp) : null;
        rateLimiter.check(RateLimiter.Action.CHAT, userId, clientIp,
                WINDOW_MINUTES, PER_WINDOW_LIMIT, properties.getSecurity().getGuestDailyChatLimit());

        long started = System.currentTimeMillis();
        Long resolvedSessionId = resolveSession(sessionId, userId, guestKey, q);

        int userSeq = nextSeq(resolvedSessionId);
        insertMessage(resolvedSessionId, userSeq, "USER", q, null, null, null, "SUCCESS", null, 0);

        AgentToolkit.AgentContext context = toolkit.collect(foodId, variantId, weight, q);
        String text = languageModel.compose(q, context);
        boolean degraded = context.uncertainty().stream().anyMatch(u -> DEGRADING_CODES.contains(u.code()));
        String intent = intentOf(q, context);
        int latency = (int) (System.currentTimeMillis() - started);

        AgentAnswer answer = new AgentAnswer(
                text,
                intent,
                context.foodReferences(),
                context.nutritionFacts(),
                List.of(),
                List.of(),
                context.sources(),
                context.uncertainty(),
                context.toolCalls(),
                languageModel.name(),
                latency,
                degraded);

        insertMessage(resolvedSessionId, userSeq + 1, "ASSISTANT", text, writeJson(answer),
                intent, q, degraded ? "DEGRADED" : "SUCCESS", degraded ? "PARTIAL_DATA" : null, latency);
        bumpSession(resolvedSessionId, q, 2);

        // 记录一次操作，供限流计数与审计（问答本身不写 operation_log 之外的东西）
        rateLimiter.record(RateLimiter.Action.CHAT, userId, clientIp, userAgent,
                degraded ? "DEGRADED" : "SUCCESS", latency);

        return new ChatOutcome(answer, titleOf(q), resolvedSessionId);
    }

    // ---------------------------------------------------------------- 会话

    /** 新建会话（登录用户归属本人；游客归 IP 派生的 guest_key）。 */
    public Map<String, Object> createSession(String title, UserPrincipal principal, String clientIp) {
        Long userId = principal.isLoggedIn() ? principal.userId() : null;
        String guestKey = userId == null ? guestKeyOf(clientIp) : null;
        String actualTitle = title == null || title.isBlank() ? "新的对话" : title.trim();
        Long id = insertSession(userId, guestKey, actualTitle);
        return sessionMap(id, actualTitle, 0, LocalDateTime.now());
    }

    /** 会话列表（仅登录用户；游客会话不跨请求可枚举）。 */
    public List<Map<String, Object>> listSessions(UserPrincipal principal) {
        if (!principal.isLoggedIn()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, title, message_count, created_at, updated_at
                FROM chat_session
                WHERE user_id = ? AND deleted_at IS NULL
                ORDER BY COALESCE(last_message_at, created_at) DESC, id DESC
                """, principal.userId());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(sessionMap(
                    ((Number) row.get("id")).longValue(),
                    (String) row.get("title"),
                    row.get("message_count") == null ? 0 : ((Number) row.get("message_count")).intValue(),
                    toLocalDateTime(row.get("updated_at") != null ? row.get("updated_at") : row.get("created_at"))));
        }
        return list;
    }

    public List<Map<String, Object>> messages(Long sessionId, UserPrincipal principal, String clientIp) {
        requireSession(sessionId, principal, clientIp);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, session_id, seq, role, content, structured_output, created_at
                FROM chat_message WHERE session_id = ? ORDER BY seq, id
                """, sessionId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ((Number) row.get("id")).longValue());
            item.put("sessionId", ((Number) row.get("session_id")).longValue());
            item.put("seq", ((Number) row.get("seq")).intValue());
            item.put("role", row.get("role"));
            item.put("content", row.get("content"));
            item.put("structuredAnswer", readJson((String) row.get("structured_output")));
            item.put("createdAt", String.valueOf(row.get("created_at")));
            list.add(item);
        }
        return list;
    }

    /** 删除会话：只允许删除本人（或本人游客键）的会话。 */
    public void deleteSession(Long sessionId, UserPrincipal principal, String clientIp) {
        requireSession(sessionId, principal, clientIp);
        jdbc.update("UPDATE chat_session SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
    }

    /**
     * 校验会话归属。
     *
     * <p>「不存在」与「不属于你」统一返回 40301：区分两者会让攻击者能枚举出哪些会话 id 存在。
     */
    private void requireSession(Long sessionId, UserPrincipal principal, String clientIp) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "会话 id 不能为空");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id, guest_key FROM chat_session WHERE id = ? AND deleted_at IS NULL", sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND, "会话不存在");
        }
        Map<String, Object> row = rows.get(0);
        Long owner = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
        boolean mine;
        if (owner != null) {
            mine = principal.isLoggedIn() && owner.equals(principal.userId());
        } else {
            String key = (String) row.get("guest_key");
            mine = !principal.isLoggedIn() && key != null && key.equals(guestKeyOf(clientIp));
        }
        if (!mine) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
    }

    // ---------------------------------------------------------------- 内部

    private Long resolveSession(Long sessionId, Long userId, String guestKey, String question) {
        if (sessionId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT user_id, guest_key FROM chat_session WHERE id = ? AND deleted_at IS NULL", sessionId);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.SESSION_NOT_FOUND, "会话不存在");
            }
            Map<String, Object> row = rows.get(0);
            Long owner = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
            String key = (String) row.get("guest_key");
            boolean mine = owner != null ? owner.equals(userId) : (userId == null && guestKey != null && guestKey.equals(key));
            if (!mine) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
            }
            return sessionId;
        }
        // 未指定会话：按第一条问题自动建一个，标题取问题前若干字
        return insertSession(userId, guestKey, titleOf(question));
    }

    private Long insertSession(Long userId, String guestKey, String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO chat_session (user_id, guest_key, title, last_message_at, message_count, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, GeneratedKeys.ID_COLUMN);
            ps.setObject(1, userId);
            ps.setString(2, guestKey);
            ps.setString(3, title);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建会话失败");
        }
        return key.longValue();
    }
    private int nextSeq(Long sessionId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(seq), 0) FROM chat_message WHERE session_id = ?", Integer.class, sessionId);
        return (max == null ? 0 : max) + 1;
    }

    private void insertMessage(Long sessionId, int seq, String role, String content, String structuredOutput,
                               String intent, String question, String status, String errorCode, int latencyMs) {
        jdbc.update("""
                INSERT INTO chat_message (session_id, seq, role, content, structured_output, intent, tool_calls,
                                          model_name, latency_ms, token_usage, status, error_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, ?, ?, CURRENT_TIMESTAMP)
                """, sessionId, seq, role, content, structuredOutput, intent,
                "USER".equals(role) ? null : languageModel.name(),
                "USER".equals(role) ? null : latencyMs, status, errorCode);
    }

    private void bumpSession(Long sessionId, String question, int addedMessages) {
        jdbc.update("""
                UPDATE chat_session
                   SET message_count = message_count + ?, last_message_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP,
                       title = CASE WHEN title IS NULL OR title = '新的对话' THEN ? ELSE title END
                 WHERE id = ?
                """, addedMessages, titleOf(question), sessionId);
    }

    private Map<String, Object> sessionMap(Long id, String title, int messageCount, LocalDateTime updatedAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("messageCount", messageCount);
        map.put("updatedAt", updatedAt == null ? null : updatedAt.toString().replace('T', ' '));
        return map;
    }

    /** 会话标题取问题前 20 字，避免把整段问题当标题。 */
    private static String titleOf(String question) {
        if (question == null || question.isBlank()) {
            return "新的对话";
        }
        String q = question.trim();
        return q.length() <= 20 ? q : q.substring(0, 20) + "…";
    }

    /**
     * 游客键：由客户端 IP 加盐派生。
     *
     * <p>实现已抽到 {@link GuestKeyResolver}——限流计数与会话归属必须用同一个派生口径，
     * 否则「同一个人」在两处会被算成两个不同的主体，配额与会话归属都会错位。
     * 它<b>只用于归集同类请求</b>，不作为鉴权依据。
     */
    private String guestKeyOf(String clientIp) {
        return guestKeys.of(clientIp);
    }

    private static String intentOf(String question, AgentToolkit.AgentContext context) {
        if (context.calculation() != null) {
            return "NUTRITION_CALCULATION";
        }
        if (context.food() == null) {
            return "GENERAL_QUESTION";
        }
        String q = question == null ? "" : question;
        if (q.contains("怎么挑") || q.contains("挑选") || q.contains("储存") || q.contains("保存")) {
            return "STORAGE_TIPS";
        }
        if (q.contains("适合") || q.contains("能吃") || q.contains("人群")) {
            return "SUITABLE_AUDIENCE";
        }
        if (q.contains("热量") || q.contains("多少卡") || q.contains("卡路里")) {
            return "ENERGY_QUERY";
        }
        return "NUTRITION_QUERY";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("结构化回答序列化失败，落库为空：{}", e.getMessage());
            return null;
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("结构化回答解析失败，按空处理：{}", e.getMessage());
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        return null;
    }
}
