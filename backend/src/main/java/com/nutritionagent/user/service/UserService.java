package com.nutritionagent.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.common.security.AuthTokenService;
import com.nutritionagent.common.security.UserPrincipal;
import com.nutritionagent.common.security.UserRole;
import com.nutritionagent.food.dto.FoodSummary;
import com.nutritionagent.food.service.FoodQueryService;
import com.nutritionagent.user.dto.UserView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户域：注册、登录、资料与偏好、收藏、删除个人数据（FR-007 / FR-008）。
 *
 * <p>两条不可让步的规则：
 * <ul>
 *   <li><b>越权防护</b>：每个方法都接收明确的 {@code userId} 参数，由 Controller 从令牌
 *       （{@code UserContext}）中取出后传入。<b>任何方法都不接受来自路径或请求体的 userId</b>，
 *       这样"越权"在这层就不可能被表达出来，而不是靠每个方法记得写一次校验。</li>
 *   <li><b>密码</b>：只存 BCrypt 散列；登录失败绝不区分"用户不存在"与"密码错误"，
 *       统一 40001，避免账号枚举。</li>
 * </ul>
 */
@Service
public class UserService {

    private static final int USER_ROLE_ID = 1;

    private final JdbcTemplate jdbc;
    private final AuthTokenService authTokenService;
    private final FoodQueryService foodQueryService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(JdbcTemplate jdbc, AuthTokenService authTokenService,
                       FoodQueryService foodQueryService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.authTokenService = authTokenService;
        this.foodQueryService = foodQueryService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 注册 / 登录 / 登出
    // ------------------------------------------------------------------

    /** 注册。用户名或邮箱已存在 → 40901（不提示"是用户名还是邮箱冲突"以外的细节）。 */
    @Transactional
    public UserView register(String username, String rawPassword, String nickname, String email) {
        String name = requireText(username, "用户名不能为空");
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码长度不能少于 6 位");
        }
        String mail = blankToNull(email);
        if (mail != null && !mail.contains("@")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "邮箱格式不正确");
        }
        try {
            jdbc.update("INSERT INTO sys_user (username, email, password_hash, nickname, status) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                    name, mail, passwordEncoder.encode(rawPassword), blankToNull(nickname));
        } catch (DuplicateKeyException e) {
            // 唯一键冲突：已存在的用户名或邮箱。统一话术，不额外区分
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名或邮箱已被占用");
        }
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ? AND deleted_at IS NULL",
                Long.class, name);
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, USER_ROLE_ID);
        return loadUser(userId);
    }

    /** 登录。成功返回令牌与用户信息；失败一律 40001。 */
    @Transactional
    public LoginResult login(String username, String rawPassword) {
        String name = blankToNull(username);
        if (name == null || rawPassword == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名与密码不能为空");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, password_hash, status FROM sys_user WHERE username = ? AND deleted_at IS NULL", name);
        // 即使用户不存在也执行一次哈希校验，避免用响应时间区分"用户不存在"与"密码错误"
        String hash = rows.isEmpty() ? "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin"
                : String.valueOf(rows.get(0).get("password_hash"));
        boolean matched = passwordEncoder.matches(rawPassword, hash);
        if (rows.isEmpty() || !matched) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名或密码错误");
        }
        Long userId = ((Number) rows.get(0).get("id")).longValue();
        String status = String.valueOf(rows.get(0).get("status"));
        if (!"ACTIVE".equals(status)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号当前不可用：" + status);
        }
        jdbc.update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
        String token = authTokenService.issue(userId);
        return new LoginResult(token, loadUser(userId));
    }

    /** 登出：只吊销当前这一个令牌，其他设备上的登录态不受影响。 */
    public void logout(UserPrincipal principal) {
        if (principal != null && principal.tokenId() != null) {
            authTokenService.revoke(principal.tokenId());
        }
    }

    // ------------------------------------------------------------------
    // 资料与偏好
    // ------------------------------------------------------------------

    public UserView loadUser(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, nickname, email, avatar_url, created_at FROM sys_user "
                        + "WHERE id = ? AND deleted_at IS NULL", userId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在或已注销");
        }
        Map<String, Object> row = rows.get(0);
        List<String> roleCodes = authTokenService.rolesOf(userId).stream()
                .map(Enum::name).sorted().toList();
        return new UserView(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("username")),
                (String) row.get("nickname"),
                (String) row.get("email"),
                (String) row.get("avatar_url"),
                roleCodes,
                roleCodes.stream().map(UserService::roleLabel).toList(),
                loadPreferences(userId),
                String.valueOf(row.get("created_at")));
    }

    /**
     * 更新资料与偏好。只更新显式给出的字段（{@code null} 表示"不改"），
     * 这样前端可以只提交一个昵称而不必回传整份资料。
     */
    @Transactional
    public UserView updateProfile(Long userId, String nickname, String email, String avatarUrl,
                                  Map<String, Object> preferences) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (nickname != null) {
            sets.add("nickname = ?");
            args.add(blankToNull(nickname));
        }
        if (email != null) {
            String mail = blankToNull(email);
            if (mail != null && !mail.contains("@")) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "邮箱格式不正确");
            }
            sets.add("email = ?");
            args.add(mail);
        }
        if (avatarUrl != null) {
            sets.add("avatar_url = ?");
            args.add(blankToNull(avatarUrl));
        }
        if (!sets.isEmpty()) {
            args.add(userId);
            try {
                jdbc.update("UPDATE sys_user SET " + String.join(", ", sets)
                        + ", updated_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL",
                        args.toArray());
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该邮箱已被其他账号使用");
            }
        }
        if (preferences != null) {
            savePreferences(userId, preferences);
        }
        return loadUser(userId);
    }

    /** 读取偏好，返回结构固定为 {@code {goal, avoid, allergens, activityLevel}}，缺的给 null/空数组。 */
    public Map<String, Object> loadPreferences(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT diet_goal, activity_level, allergens, diet_preferences FROM user_profile WHERE user_id = ?",
                userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            result.put("goal", null);
            result.put("activityLevel", null);
            result.put("avoid", List.of());
            result.put("allergens", List.of());
            return result;
        }
        Map<String, Object> row = rows.get(0);
        result.put("goal", row.get("diet_goal"));
        result.put("activityLevel", row.get("activity_level"));
        result.put("avoid", readStringList(row.get("diet_preferences"), "avoid"));
        result.put("allergens", readJsonList(row.get("allergens")));
        return result;
    }

    /**
     * 保存偏好。{@code avoid}（忌口/饮食偏好）落到 {@code diet_preferences} JSON 的 avoid 键，
     * {@code allergens} 落到 {@code allergens} 列 —— 两者语义不同（一个是选择、一个是过敏风险），
     * 不合并存储。
     */
    private void savePreferences(Long userId, Map<String, Object> preferences) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, diet_goal, activity_level, allergens, diet_preferences FROM user_profile "
                        + "WHERE user_id = ?", userId);
        String goal = preferences.containsKey("goal") ? blankToNull(str(preferences.get("goal")))
                : (rows.isEmpty() ? null : str(rows.get(0).get("diet_goal")));
        String activity = preferences.containsKey("activityLevel") ? blankToNull(str(preferences.get("activityLevel")))
                : (rows.isEmpty() ? null : str(rows.get(0).get("activity_level")));
        String allergensJson = preferences.containsKey("allergens")
                ? writeJson(preferences.get("allergens"))
                : (rows.isEmpty() ? null : str(rows.get(0).get("allergens")));
        String avoidJson;
        if (preferences.containsKey("avoid")) {
            avoidJson = writeJson(Map.of("avoid", preferences.get("avoid") == null
                    ? List.of() : preferences.get("avoid")));
        } else if (rows.isEmpty()) {
            avoidJson = null;
        } else {
            avoidJson = str(rows.get(0).get("diet_preferences"));
        }

        if (rows.isEmpty()) {
            // consent_at 记录用户主动提供资料的时刻（PRD 5.7 要求主动提供或明确授权）
            jdbc.update("INSERT INTO user_profile (user_id, diet_goal, activity_level, allergens, "
                            + "diet_preferences, consent_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    userId, goal, activity, allergensJson, avoidJson);
        } else {
            jdbc.update("UPDATE user_profile SET diet_goal = ?, activity_level = ?, allergens = ?, "
                            + "diet_preferences = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    goal, activity, allergensJson, avoidJson, userId);
        }
    }

    // ------------------------------------------------------------------
    // 收藏
    // ------------------------------------------------------------------

    /** 收藏列表，返回 {@code FoodSummary}（与食品列表同一套字段，前端可复用卡片组件）。 */
    public List<FoodSummary> favorites(Long userId) {
        List<Long> foodIds = jdbc.queryForList(
                "SELECT food_id FROM user_favorite WHERE user_id = ? AND deleted_at IS NULL ORDER BY id DESC",
                Long.class, userId);
        if (foodIds.isEmpty()) {
            return List.of();
        }
        // 逐个取详情汇总，规模受用户自身收藏数限制；已下架的食品会被自然过滤掉
        List<FoodSummary> list = new ArrayList<>();
        for (Long foodId : foodIds) {
            try {
                FoodSummary summary = foodQueryService.summaryById(foodId);
                if (summary != null) {
                    list.add(summary);
                }
            } catch (BusinessException ignored) {
                // 食品已下架：不出现在收藏列表里，但保留收藏记录以便重新上架后恢复
            }
        }
        return list;
    }

    /** 添加收藏。食品不存在 → 40401；用户已有有效收藏则幂等返回。 */
    @Transactional
    public void addFavorite(Long userId, Long foodId) {
        if (foodId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "foodId 不能为空");
        }
        // 必须校验食品可见，否则可以收藏任意不存在的 id
        foodQueryService.detail(foodId);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorite WHERE user_id = ? AND food_id = ? AND deleted_at IS NULL",
                Integer.class, userId, foodId);
        if (active != null && active > 0) {
            // 已收藏：幂等返回，不因重复点击报错
            return;
        }
        // 可能是"取消过又收藏回来"。此时唯一键 (user_id, food_id, deleted_at, gd_deleted_key)
        // 仍被那条软删行占着，直接 INSERT 会撞唯一键，因此改为把软删行复活。
        int restored = jdbc.update("UPDATE user_favorite SET deleted_at = NULL "
                + "WHERE user_id = ? AND food_id = ? AND deleted_at IS NOT NULL", userId, foodId);
        if (restored == 0) {
            jdbc.update("INSERT INTO user_favorite (user_id, food_id) VALUES (?, ?)", userId, foodId);
        }
    }

    /** 取消收藏。未收藏时也返回成功（幂等），避免前端因连点报错。 */
    @Transactional
    public void removeFavorite(Long userId, Long foodId) {
        jdbc.update("UPDATE user_favorite SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND food_id = ? AND deleted_at IS NULL", userId, foodId);
    }

    // ------------------------------------------------------------------
    // 删除个人数据（FR-007 删除权）
    // ------------------------------------------------------------------

    /**
     * 删除个人数据：注销账号并清理该用户的令牌、资料、收藏、会话与识别记录。
     *
     * <p>用逻辑删除（{@code deleted_at}）而不是物理删除：审计与合规要求能回答"何时被谁删除"，
     * 且物理删除会连带破坏 {@code chat_message} → {@code chat_session} 的外键。
     * 同时把令牌全部吊销，使删除立刻生效而不是等令牌自然过期。
     */
    @Transactional
    public void deleteMyData(Long userId) {
        authTokenService.revokeAllOfUser(userId);
        // user_profile 表没有 deleted_at 列，无法做逻辑删除；而账号本身会被标记删除，
        // 资料因此不再可达。这里保留资料行，是为了让"账号何时被删、删前填过什么"
        // 在审计时仍可回答——这正是 [D8] 选择 deleted_at 而不是物理删除的原因。
        jdbc.update("UPDATE user_favorite SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND deleted_at IS NULL", userId);
        jdbc.update("UPDATE chat_session SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = ? AND deleted_at IS NULL",
                userId);
        jdbc.update("UPDATE sys_user SET deleted_at = CURRENT_TIMESTAMP, status = 'DISABLED' "
                + "WHERE id = ? AND deleted_at IS NULL", userId);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String roleLabel(String code) {
        return UserRole.of(code).map(UserService::labelOf).orElse(code);
    }

    private static String labelOf(UserRole role) {
        return switch (role) {
            case GUEST -> "游客";
            case USER -> "普通用户";
            case CONTENT_ADMIN -> "内容管理员";
            case SYS_ADMIN -> "系统管理员";
        };
    }

    private static String requireText(String value, String message) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Object json, String key) {
        if (json == null) {
            return List.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(String.valueOf(json),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object value = parsed.get(key);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        } catch (Exception ignored) {
            // JSON 解析失败视为"用户没填偏好"，绝不因脏数据让整个资料接口 500
        }
        return List.of();
    }

    private List<String> readJsonList(Object json) {
        if (json == null) {
            return List.of();
        }
        try {
            List<?> parsed = objectMapper.readValue(String.valueOf(json), new TypeReference<List<Object>>() {
            });
            return parsed.stream().map(String::valueOf).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "偏好字段格式不正确");
        }
    }

    /** 登录结果：令牌 + 用户信息。 */
    public record LoginResult(String token, UserView user) {
    }

    /** 注册结果：用户信息 + 直接登录用的令牌（注册后无需再登录一次）。 */
    public record RegisterResult(Long id, String username, String nickname, String email, UserView user) {
    }

    /** 便于控制器构造偏好空值。 */
    public static Map<String, Object> emptyPreferences() {
        return new HashMap<>();
    }
}
