package com.nutritionagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritionagent.agent.dto.ImageRecognitionResult;
import com.nutritionagent.agent.vision.VisionProvider;
import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.common.file.FileStorageService;
import com.nutritionagent.common.file.ImageValidator;
import com.nutritionagent.common.ratelimit.RateLimiter;
import com.nutritionagent.common.security.UserPrincipal;
import com.nutritionagent.food.service.FoodQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nutritionagent.common.db.GeneratedKeys;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片识别（FR-006）。
 *
 * <p>职责边界（PRD 5.6）：本服务只负责「图片 → 候选食品 ID」这一段，并把结果落库。
 * 营养数值一律由 {@link com.nutritionagent.nutrition.service.NutritionQueryService} 在
 * 用户确认食品之后提供，识别结果里<b>不含任何营养数字</b>。
 */
@Service
public class RecognitionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionService.class);

    /** 登录用户每小时 20 次（契约第五章）。 */
    private static final int WINDOW_MINUTES = 60;
    private static final int PER_WINDOW_LIMIT = 20;

    private static final String NOTICE_SUCCESS =
            "识别结果仅为候选，请您确认后再查看营养数据。混合菜品的食材、重量及烹饪用油无法仅凭图片确定。";
    private static final String NOTICE_MANUAL =
            "未能从图片中匹配到食品，请手动搜索食品名称。";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ImageValidator imageValidator;
    private final FileStorageService storage;
    private final VisionProvider visionProvider;
    private final RateLimiter rateLimiter;
    private final FoodQueryService foodQueryService;

    public RecognitionService(JdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              ImageValidator imageValidator,
                              FileStorageService storage,
                              VisionProvider visionProvider,
                              RateLimiter rateLimiter,
                              FoodQueryService foodQueryService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.imageValidator = imageValidator;
        this.storage = storage;
        this.visionProvider = visionProvider;
        this.rateLimiter = rateLimiter;
        this.foodQueryService = foodQueryService;
    }

    /**
     * 上传并识别。
     *
     * @param principal 当前主体（游客时为 {@link UserPrincipal#GUEST}）
     */
    public ImageRecognitionResult recognize(MultipartFile file, UserPrincipal principal,
                                            String clientIp, String userAgent,
                                            int guestDailyLimit) {
        Long userId = principal.isLoggedIn() ? principal.userId() : null;
        rateLimiter.check(RateLimiter.Action.RECOGNIZE, userId, clientIp, WINDOW_MINUTES, PER_WINDOW_LIMIT, guestDailyLimit);

        long started = System.currentTimeMillis();
        ImageValidator.Detected detected = imageValidator.validate(file);
        FileStorageService.Stored stored = storage.store(file, "recognition", detected.extension());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "读取上传文件失败：" + e.getMessage());
        }
        VisionProvider.Outcome outcome = visionProvider.recognize(bytes, detected.mimeType());

        List<ImageRecognitionResult.Candidate> candidates = enrich(outcome.candidates());
        String status = candidates.isEmpty() && "SUCCESS".equals(outcome.status())
                // 防御：识别层声称成功却没有候选，对用户而言等同于没识别出来
                ? "NEED_MANUAL_SEARCH"
                : outcome.status();
        int latencyMs = (int) (System.currentTimeMillis() - started);
        String notice = "NEED_MANUAL_SEARCH".equals(status) ? NOTICE_MANUAL : NOTICE_SUCCESS;

        Long id = insert(principal, stored, file, detected, status, candidates, outcome.failureReason(),
                outcome.modelName(), latencyMs);
        // 识别完成后记一次操作，供限流与审计计数
        rateLimiter.record(RateLimiter.Action.RECOGNIZE, userId, clientIp, userAgent, status, latencyMs);

        return new ImageRecognitionResult(id, status, stored.publicUrl(), candidates, null,
                notice, outcome.failureReason(), latencyMs);
    }

    /** 查询一次识别结果（只允许本人查看）。 */
    public ImageRecognitionResult get(Long recognitionId, UserPrincipal principal) {
        Map<String, Object> row = requireOwned(recognitionId, principal);
        return toResult(row);
    }

    /**
     * 用户确认识别结果（契约 4.5）。
     *
     * <p>确认只写入"用户认定这是哪个食品"，<b>不复制任何营养数据</b>——后续查询仍然走
     * 食品详情与营养接口，保证数值永远只有一个来源。
     */
    public ImageRecognitionResult confirm(Long recognitionId, Long foodId, UserPrincipal principal) {
        Map<String, Object> row = requireOwned(recognitionId, principal);
        if (foodId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择要确认的食品");
        }
        // 食品必须真实可见，否则确认结果指向一个不存在的食品
        foodQueryService.detail(foodId);
        jdbc.update("UPDATE image_recognition SET confirmed_food_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                foodId, recognitionId);
        row.put("confirmed_food_id", foodId);
        return toResult(row);
    }

    // ---------------------------------------------------------------- 内部

    /** 取一行识别记录并校验归属；不存在或不属于本人一律 40301（不区分，避免探测他人资源）。 */
    private Map<String, Object> requireOwned(Long recognitionId, UserPrincipal principal) {
        if (recognitionId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "识别记录 id 不能为空");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, user_id, storage_path, candidates, confirmed_food_id, status,
                       failure_reason, latency_ms
                FROM image_recognition WHERE id = ?
                """, recognitionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "识别记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        Long owner = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
        if (owner == null || !principal.isLoggedIn() || !owner.equals(principal.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该识别记录");
        }
        return row;
    }

    /** 把识别层给出的 foodId 补全成前端需要的名称与封面图；查不到的候选直接丢弃。 */
    private List<ImageRecognitionResult.Candidate> enrich(List<VisionProvider.Candidate> raw) {
        List<ImageRecognitionResult.Candidate> list = new ArrayList<>();
        for (VisionProvider.Candidate c : raw) {
            if (c.foodId() == null) {
                continue;
            }
            Map<String, Object> food = queryFood(c.foodId());
            if (food == null) {
                // 模型可能给出不存在的 id；丢弃而不是伪造一个名字
                log.warn("视觉候选 foodId={} 在食品库中不可见，已丢弃该候选", c.foodId());
                continue;
            }
            String name = (String) food.get("name");
            String cover = (String) food.get("cover");
            list.add(new ImageRecognitionResult.Candidate(
                    c.foodId(),
                    name != null ? name : c.foodName(),
                    c.confidence(),
                    c.reason(),
                    cover));
        }
        return list;
    }

    private Map<String, Object> queryFood(Long foodId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.id, f.name,
                       (SELECT fi.url FROM food_image fi
                         WHERE fi.food_id = f.id AND fi.deleted_at IS NULL
                         ORDER BY fi.is_cover DESC, fi.sort_order, fi.id LIMIT 1) AS cover
                FROM food f
                WHERE f.id = ? AND f.deleted_at IS NULL
                  AND f.publish_status = 'PUBLISHED' AND f.review_status = 'APPROVED'
                """, foodId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long insert(UserPrincipal principal, FileStorageService.Stored stored, MultipartFile file,
                        ImageValidator.Detected detected, String status,
                        List<ImageRecognitionResult.Candidate> candidates,
                        String failureReason, String modelName, int latencyMs) {
        String candidatesJson = writeJson(candidates);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO image_recognition
                      (user_id, session_id, storage_path, original_name, mime_type, file_size, file_hash,
                       candidates, confirmed_food_id, status, failure_reason, model_name, latency_ms, created_at, updated_at)
                    VALUES (?, NULL, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, GeneratedKeys.ID_COLUMN);
            ps.setObject(1, principal.isLoggedIn() ? principal.userId() : null);
            ps.setString(2, stored.relativePath());
            ps.setString(3, file.getOriginalFilename());
            ps.setString(4, detected.mimeType());
            ps.setLong(5, stored.size());
            ps.setString(6, stored.sha256());
            ps.setString(7, candidatesJson);
            ps.setString(8, status);
            ps.setString(9, failureReason);
            ps.setString(10, modelName);
            ps.setInt(11, latencyMs);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private ImageRecognitionResult toResult(Map<String, Object> row) {
        Long id = ((Number) row.get("id")).longValue();
        String status = (String) row.get("status");
        Long confirmed = row.get("confirmed_food_id") == null ? null
                : ((Number) row.get("confirmed_food_id")).longValue();
        String relative = (String) row.get("storage_path");
        Integer latency = row.get("latency_ms") == null ? null : ((Number) row.get("latency_ms")).intValue();
        String notice = "NEED_MANUAL_SEARCH".equals(status) ? NOTICE_MANUAL : NOTICE_SUCCESS;

        List<Map<String, Object>> stored = readJson((String) row.get("candidates"));
        List<ImageRecognitionResult.Candidate> candidates = new ArrayList<>();
        for (Map<String, Object> c : stored) {
            candidates.add(new ImageRecognitionResult.Candidate(
                    asLong(c.get("foodId")),
                    (String) c.get("foodName"),
                    asDouble(c.get("confidence")),
                    (String) c.get("reason"),
                    (String) c.get("coverImageUrl")));
        }
        return new ImageRecognitionResult(id, status, storage.publicUrlOf(relative), candidates,
                confirmed, notice, (String) row.get("failure_reason"), latency);
    }

    private String writeJson(List<ImageRecognitionResult.Candidate> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (Exception e) {
            // 候选序列化失败不该让整次识别失败：落一个空数组，用户仍能看到"请手动搜索"
            log.warn("识别候选序列化失败，落库为空数组：{}", e.getMessage());
            return "[]";
        }
    }

    private List<Map<String, Object>> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<LinkedHashMap<String, Object>> parsed =
                    objectMapper.readValue(json, new TypeReference<List<LinkedHashMap<String, Object>>>() {
                    });
            return new ArrayList<>(parsed);
        } catch (Exception e) {
            // 库里存的 JSON 脏了：返回空候选而不是让查询接口 500
            log.warn("识别候选 JSON 解析失败，按无候选处理：{}", e.getMessage());
            return List.of();
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
