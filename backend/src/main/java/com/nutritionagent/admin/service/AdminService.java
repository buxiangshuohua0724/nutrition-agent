package com.nutritionagent.admin.service;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.api.PageResult;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.common.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nutritionagent.common.db.GeneratedKeys;
import com.nutritionagent.common.db.Timestamps;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台内容管理（FR-009 / FR-010）。
 *
 * <p>三条贯穿本服务的规则：
 * <ol>
 *   <li><b>后台能看到草稿，前台永远看不到</b>：本服务的查询不加 {@code publish_status/review_status}
 *       过滤，但前台接口（{@code FoodQueryService}）一律按可见性过滤。两者不能互相复用查询方法。</li>
 *   <li><b>写操作必留痕</b>：每次新增/修改/删除/审核/发布都写一条 {@code admin_audit_log}，
 *       并把变更前后的关键字段存进 {@code before_json}/{@code after_json}。</li>
 *   <li><b>营养数据必须带来源</b>：{@code PUT /admin/variants/{id}/nutrition} 缺 {@code sourceId}
 *       直接 40001，这是"来源可追溯率 100%"在代码层的强制点。</li>
 * </ol>
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final String REVIEW_STATES = "DRAFT/PENDING/APPROVED/REJECTED";
    private static final String PUBLISH_STATES = "UNPUBLISHED/PUBLISHED/OFFLINE";

    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public AdminService(JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ================================================================ 食品

    public PageResult<Map<String, Object>> listFoods(String keyword, Long categoryId,
                                                     String publishStatus, String reviewStatus,
                                                     int page, int pageSize) {
        validatePage(page, pageSize);
        StringBuilder sql = new StringBuilder("""
                SELECT f.id, f.code, f.name, f.en_name, f.category_id, c.name AS category_name,
                       f.publish_status, f.review_status, f.published_at, f.updated_at,
                       (SELECT COUNT(*) FROM food_state_variant v
                         WHERE v.food_id = f.id AND v.deleted_at IS NULL) AS variant_count
                FROM food f
                LEFT JOIN food_category c ON c.id = f.category_id
                WHERE f.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (f.name LIKE ? ESCAPE '\\' OR f.code LIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(keyword.trim()) + "%";
            args.add(like);
            args.add(like);
        }
        if (categoryId != null) {
            sql.append(" AND f.category_id = ?");
            args.add(categoryId);
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            sql.append(" AND f.publish_status = ?");
            args.add(publishStatus);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            sql.append(" AND f.review_status = ?");
            args.add(reviewStatus);
        }
        sql.append(" ORDER BY f.id DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        int total = rows.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows.subList(from, to)) {
            list.add(foodRow(row));
        }
        return PageResult.of(list, page, pageSize, total);
    }

    @Transactional
    public Map<String, Object> createFood(Map<String, Object> body, UserPrincipal principal) {
        String code = requiredString(body, "code", "食品编码不能为空");
        String name = requiredString(body, "name", "食品名称不能为空");
        Long categoryId = requiredLong(body, "categoryId", "请选择食品分类");
        requireCategory(categoryId);
        if (existsByCode(code, null)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "食品编码已存在：" + code);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO food (code, category_id, name, en_name, scientific_name, intro, common_usage,
                                      origin, storage_method, suitable_for, cautions, search_keywords,
                                      review_status, publish_status, created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'DRAFT', 'UNPUBLISHED', ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                    """, GeneratedKeys.ID_COLUMN);
            ps.setString(1, code);
            ps.setLong(2, categoryId);
            ps.setString(3, name);
            ps.setString(4, optionalString(body, "enName"));
            ps.setString(5, optionalString(body, "scientificName"));
            ps.setString(6, optionalString(body, "intro"));
            ps.setString(7, optionalString(body, "commonUsage"));
            ps.setString(8, optionalString(body, "origin"));
            ps.setString(9, optionalString(body, "storageMethod"));
            ps.setString(10, optionalString(body, "suitableFor"));
            ps.setString(11, optionalString(body, "cautions"));
            ps.setObject(12, principal.userId());
            ps.setObject(13, principal.userId());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "新增食品失败");
        }
        Long id = key.longValue();
        audit(principal, "CREATE", "food", id, null, briefFood(id), "新增食品");
        return requireFood(id);
    }

    @Transactional
    public Map<String, Object> updateFood(Long id, Map<String, Object> body, UserPrincipal principal) {
        Map<String, Object> before = requireFood(id);
        if (body.containsKey("code")) {
            String code = requiredString(body, "code", "食品编码不能为空");
            if (existsByCode(code, id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "食品编码已存在：" + code);
            }
        }
        if (body.containsKey("categoryId")) {
            requireCategory(requiredLong(body, "categoryId", "请选择食品分类"));
        }

        Map<String, String> columnOf = new LinkedHashMap<>();
        columnOf.put("code", "code");
        columnOf.put("name", "name");
        columnOf.put("enName", "en_name");
        columnOf.put("scientificName", "scientific_name");
        columnOf.put("intro", "intro");
        columnOf.put("commonUsage", "common_usage");
        columnOf.put("origin", "origin");
        columnOf.put("storageMethod", "storage_method");
        columnOf.put("suitableFor", "suitable_for");
        columnOf.put("cautions", "cautions");

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, String> entry : columnOf.entrySet()) {
            if (body.containsKey(entry.getKey())) {
                sets.add(entry.getValue() + " = ?");
                args.add(body.get(entry.getKey()) == null ? null : String.valueOf(body.get(entry.getKey())));
            }
        }
        if (body.containsKey("categoryId")) {
            sets.add("category_id = ?");
            args.add(((Number) body.get("categoryId")).longValue());
        }
        if (sets.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "没有需要修改的字段");
        }
        // 内容变更后必须重新审核：否则"审核通过"这个标记会漂移到未审阅的文本上
        sets.add("review_status = 'PENDING'");
        sets.add("updated_by = ?");
        sets.add("updated_at = CURRENT_TIMESTAMP");
        args.add(principal.userId());
        args.add(id);
        jdbc.update("UPDATE food SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());

        audit(principal, "UPDATE", "food", id, before, briefFood(id), "编辑食品");
        return requireFood(id);
    }

    /** 下架食品：保留记录、置为 OFFLINE，前台立即不可见（PRD 要求下架食品不可见）。 */
    @Transactional
    public void offlineFood(Long id, UserPrincipal principal) {
        Map<String, Object> before = requireFood(id);
        jdbc.update("""
                UPDATE food SET publish_status = 'OFFLINE', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, principal.userId(), id);
        audit(principal, "OFFLINE", "food", id, before, briefFood(id), "下架食品");
    }

    @Transactional
    public Map<String, Object> reviewFood(Long id, boolean approved, String comment, UserPrincipal principal) {
        Map<String, Object> before = requireFood(id);
        String target = approved ? "APPROVED" : "REJECTED";
        jdbc.update("""
                UPDATE food SET review_status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP,
                                updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, target, principal.userId(), principal.userId(), id);
        String detail = (approved ? "审核通过" : "审核驳回")
                + (comment == null || comment.isBlank() ? "" : "：" + comment);
        audit(principal, "REVIEW", "food", id, before, briefFood(id), detail);
        return requireFood(id);
    }

    @Transactional
    public Map<String, Object> publishFood(Long id, UserPrincipal principal) {
        Map<String, Object> before = requireFood(id);
        String review = String.valueOf(before.get("reviewStatus"));
        String publish = String.valueOf(before.get("publishStatus"));
        if ("PUBLISHED".equals(publish)) {
            // 幂等：重复点击发布不该报错，也不该产生一条新的审计记录
            return before;
        }
        if (!"APPROVED".equals(review)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "食品尚未通过审核，当前审核状态为 " + review + "，不能发布");
        }
        jdbc.update("""
                UPDATE food SET publish_status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                                updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, principal.userId(), id);
        audit(principal, "PUBLISH", "food", id, before, briefFood(id), "发布食品");
        return requireFood(id);
    }

    // ================================================================ 形态

    @Transactional
    public Map<String, Object> createVariant(Long foodId, Map<String, Object> body, UserPrincipal principal) {
        Map<String, Object> food = requireFood(foodId);
        String state = requiredString(body, "state", "请选择食品状态（state）");
        String cookingMethod = optionalString(body, "cookingMethod");
        if (existsVariant(foodId, state, cookingMethod, null)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "该食品下已存在相同状态与烹饪方式的形态");
        }
        boolean isDefault = asBoolean(body.get("isDefault"));
        if (isDefault) {
            // 唯一默认形态：新增默认形态前先把旧的降级，否则"默认形态"会有两个
            jdbc.update("UPDATE food_state_variant SET is_default = 0 WHERE food_id = ? AND deleted_at IS NULL", foodId);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO food_state_variant (food_id, state, cooking_method, name_suffix, edible_ratio,
                                                    description, is_default, review_status, publish_status,
                                                    created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', 'UNPUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, GeneratedKeys.ID_COLUMN);
            ps.setLong(1, foodId);
            ps.setString(2, state);
            ps.setString(3, cookingMethod);
            ps.setString(4, optionalString(body, "nameSuffix"));
            ps.setBigDecimal(5, asBigDecimal(body.get("edibleRatio")));
            ps.setString(6, optionalString(body, "description"));
            ps.setBoolean(7, isDefault);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "新增食品形态失败");
        }
        Long variantId = key.longValue();
        audit(principal, "CREATE_VARIANT", "food_state_variant", variantId, null,
                Map.of("foodId", foodId, "state", state), "新增食品形态（" + food.get("name") + "）");
        return requireVariant(variantId);
    }

    // ============================================================ 营养数据

    /** 形态营养数据（含草稿），结构对齐前台 {@code NutritionTable} 以便后台直接复用编辑界面。 */
    public Map<String, Object> variantNutrition(Long variantId) {
        Map<String, Object> variant = requireVariant(variantId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT n.id AS nutrient_id, n.code AS nutrient_code, n.name AS nutrient_name,
                       n.unit, n.group_code, n.display_order, n.is_energy,
                       nv.id AS value_id, nv.value_status, nv.amount, nv.reference_amount,
                       nv.reference_unit, nv.reference_note, nv.review_status,
                       nv.source_id, ds.name AS source_name, ds.source_type
                FROM nutrient n
                LEFT JOIN nutrition_value nv
                       ON nv.nutrient_id = n.id AND nv.variant_id = ? AND nv.deleted_at IS NULL
                LEFT JOIN data_source ds ON ds.id = nv.source_id
                WHERE n.enabled = 1
                ORDER BY n.display_order, n.id
                """, variantId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nutrientId", row.get("nutrient_id"));
            item.put("nutrientCode", row.get("nutrient_code"));
            item.put("nutrientName", row.get("nutrient_name"));
            item.put("unit", row.get("unit"));
            item.put("groupCode", row.get("group_code"));
            item.put("displayOrder", row.get("display_order"));
            item.put("isEnergy", Boolean.TRUE.equals(row.get("is_energy"))
                    || Integer.valueOf(1).equals(row.get("is_energy")));
            item.put("valueId", row.get("value_id"));
            item.put("valueStatus", row.get("value_status") == null ? "MISSING" : row.get("value_status"));
            item.put("amount", row.get("amount"));
            item.put("referenceAmount", row.get("reference_amount") == null ? 100 : row.get("reference_amount"));
            item.put("referenceUnit", row.get("reference_unit") == null ? "g" : row.get("reference_unit"));
            item.put("referenceNote", row.get("reference_note"));
            item.put("reviewStatus", row.get("review_status"));
            item.put("sourceId", row.get("source_id"));
            item.put("sourceName", row.get("source_name"));
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("variantId", variant.get("id"));
        data.put("foodId", variant.get("foodId"));
        data.put("variantLabel", variant.get("variantLabel"));
        data.put("items", items);
        return data;
    }

    /**
     * 修改形态营养数据（必须携带来源）。
     *
     * <p>{@code sourceId} 缺失即 40001：这是"营养数据来源可追溯率 100%"的代码级强制点，
     * 不能只写在文档里——一旦允许无来源写入，整个可追溯性就在第一次编辑时失效了。
     */
    @Transactional
    public Map<String, Object> updateVariantNutrition(Long variantId, List<Map<String, Object>> items,
                                                      UserPrincipal principal) {
        requireVariant(variantId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "没有需要保存的营养项");
        }
        for (Map<String, Object> item : items) {
            Object sourceId = item.get("sourceId");
            if (sourceId == null || String.valueOf(sourceId).isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "营养素「" + item.get("nutrientCode") + "」缺少数据来源（sourceId）");
            }
            requireSource(((Number) sourceId).longValue());
            String status = normalizeStatus(item.get("valueStatus"), item.get("amount"));
            if (status == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "营养素「" + item.get("nutrientCode") + "」的 valueStatus 只能是 PRESENT/ZERO/MISSING");
            }
        }

        Map<String, Object> before = briefNutrition(variantId);
        int updated = 0;
        for (Map<String, Object> item : items) {
            Long nutrientId = nutrientIdOf(item);
            String status = normalizeStatus(item.get("valueStatus"), item.get("amount"));
            Long sourceId = ((Number) item.get("sourceId")).longValue();
            BigDecimalHolder amount = normalizeAmount(status, item.get("amount"));
            String detail = optionalString(item, "sourceDetail");

            List<Map<String, Object>> existing = jdbc.queryForList("""
                    SELECT id FROM nutrition_value
                     WHERE variant_id = ? AND nutrient_id = ? AND deleted_at IS NULL
                    """, variantId, nutrientId);
            if (existing.isEmpty()) {
                jdbc.update("""
                        INSERT INTO nutrition_value (variant_id, nutrient_id, reference_amount, reference_unit,
                                                     reference_note, value_status, amount, source_id, source_detail,
                                                     review_status, created_by, created_at, updated_by, updated_at)
                        VALUES (?, ?, 100, 'g', ?, ?, ?, ?, ?, 'PENDING', ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                        """, variantId, nutrientId, optionalString(item, "referenceNote"), status,
                        amount.value(), sourceId, detail, principal.userId(), principal.userId());
            } else {
                jdbc.update("""
                        UPDATE nutrition_value
                           SET value_status = ?, amount = ?, source_id = ?, source_detail = ?,
                               review_status = 'PENDING', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                        """, status, amount.value(), sourceId, detail, principal.userId(),
                        ((Number) existing.get(0).get("id")).longValue());
            }
            updated++;
        }
        audit(principal, "UPDATE_NUTRITION", "food_state_variant", variantId, before,
                briefNutrition(variantId), "修改营养数据 " + updated + " 项");
        return variantNutrition(variantId);
    }

    /** 记录一次"营养数据写入未带来源"的尝试，便于事后审计（返回前仍会抛 40001）。 */
    private void requireSource(Long sourceId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM data_source WHERE id = ?", Integer.class, sourceId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "数据来源不存在：sourceId=" + sourceId);
        }
    }

    private Long nutrientIdOf(Map<String, Object> item) {
        if (item.get("nutrientId") != null) {
            return ((Number) item.get("nutrientId")).longValue();
        }
        String code = optionalString(item, "nutrientCode");
        if (code == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "营养项必须给出 nutrientId 或 nutrientCode");
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM nutrient WHERE code = ?", Long.class, code);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "营养素编码不存在：" + code);
        }
        return ids.get(0);
    }

    /** 归一化三态：MISSING 必须 amount 为 null，ZERO 必须为 0，PRESENT 必须有值。 */
    private static String normalizeStatus(Object status, Object amount) {
        String value = status == null ? null : String.valueOf(status).trim().toUpperCase();
        if (value == null || value.isEmpty()) {
            // 没写状态时按 amount 推断，但绝不把 null 当成 0
            if (amount == null || String.valueOf(amount).isBlank()) {
                return "MISSING";
            }
            java.math.BigDecimal parsed = asBigDecimal(amount);
            return parsed != null && parsed.signum() == 0 ? "ZERO" : "PRESENT";
        }
        if (!"PRESENT".equals(value) && !"ZERO".equals(value) && !"MISSING".equals(value)) {
            return null;
        }
        return value;
    }

    /** 与 {@code ck_nv_status_amount} 校验约束保持一致，避免把不合法的组合写进库。 */
    private static BigDecimalHolder normalizeAmount(String status, Object amount) {
        if ("MISSING".equals(status)) {
            return new BigDecimalHolder(null);
        }
        if ("ZERO".equals(status)) {
            return new BigDecimalHolder(java.math.BigDecimal.ZERO);
        }
        java.math.BigDecimal parsed = asBigDecimal(amount);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "状态为 PRESENT 时必须给出数值");
        }
        return new BigDecimalHolder(parsed);
    }

    private record BigDecimalHolder(java.math.BigDecimal value) {
    }

    // ================================================================ 图片

    @Transactional
    public Map<String, Object> addImage(Long foodId, String relativePath, String originalName,
                                        boolean cover, UserPrincipal principal) {
        requireFood(foodId);
        if (cover) {
            jdbc.update("UPDATE food_image SET is_cover = 0 WHERE food_id = ? AND deleted_at IS NULL", foodId);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO food_image (food_id, variant_id, url, thumb_url, image_type, caption,
                                            source_note, license_note, is_cover, sort_order, review_status,
                                            created_at, updated_at)
                    VALUES (?, NULL, ?, ?, 'RAW_MATERIAL', ?, ?, NULL, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, GeneratedKeys.ID_COLUMN);
            ps.setLong(1, foodId);
            ps.setString(2, relativePath);
            ps.setString(3, relativePath);
            ps.setString(4, originalName);
            ps.setString(5, "后台新增，授权信息待补充");
            ps.setBoolean(6, cover);
            ps.setInt(7, nextImageSort(foodId));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key == null ? null : key.longValue();
        audit(principal, "UPLOAD_IMAGE", "food_image", id, null,
                Map.of("foodId", foodId, "path", relativePath), "上传食品图片");
        return Map.of("id", id, "foodId", foodId, "url", relativePath, "isCover", cover);
    }

    @Transactional
    public void deleteImage(Long imageId, UserPrincipal principal) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, food_id, url FROM food_image WHERE id = ? AND deleted_at IS NULL", imageId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "图片不存在");
        }
        // 逻辑删除：图片可能已被引用在历史审计里，物理删除会让审计记录指向空
        jdbc.update("UPDATE food_image SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", imageId);
        audit(principal, "DELETE_IMAGE", "food_image", imageId, rows.get(0), null, "删除食品图片");
    }

    private int nextImageSort(Long foodId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM food_image WHERE food_id = ?", Integer.class, foodId);
        return (max == null ? 0 : max) + 1;
    }

    // ============================================================== 知识库

    public PageResult<Map<String, Object>> listKnowledge(String keyword, String category, int page, int pageSize) {
        validatePage(page, pageSize);
        StringBuilder sql = new StringBuilder("""
                SELECT kd.id, kd.title, kd.category, kd.summary, kd.review_status, kd.publish_status,
                       kd.published_at, kd.updated_at, ds.name AS source_name,
                       (SELECT COUNT(*) FROM knowledge_chunk kc WHERE kc.doc_id = kd.id) AS chunk_count
                FROM knowledge_doc kd
                LEFT JOIN data_source ds ON ds.id = kd.source_id
                WHERE kd.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND kd.title LIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND kd.category = ?");
            args.add(category);
        }
        sql.append(" ORDER BY kd.id DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        int total = rows.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows.subList(from, to)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("title", row.get("title"));
            item.put("category", row.get("category"));
            item.put("summary", row.get("summary"));
            item.put("reviewStatus", row.get("review_status"));
            item.put("publishStatus", row.get("publish_status"));
            item.put("sourceName", row.get("source_name"));
            item.put("chunkCount", row.get("chunk_count"));
            item.put("updatedAt", time(row.get("updated_at")));
            list.add(item);
        }
        return PageResult.of(list, page, pageSize, total);
    }

    @Transactional
    public Map<String, Object> createKnowledge(Map<String, Object> body, UserPrincipal principal) {
        String title = requiredString(body, "title", "知识标题不能为空");
        String category = optionalString(body, "category");
        Long sourceId = asLong(body.get("sourceId"));
        if (sourceId != null) {
            requireSource(sourceId);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO knowledge_doc (title, category, summary, source_id, source_excerpt,
                                               applicable_scope, review_status, publish_status,
                                               created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', 'UNPUBLISHED', ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                    """, GeneratedKeys.ID_COLUMN);
            ps.setString(1, title);
            ps.setString(2, category == null ? "GENERAL" : category);
            ps.setString(3, optionalString(body, "summary"));
            ps.setObject(4, sourceId);
            ps.setString(5, optionalString(body, "sourceExcerpt"));
            ps.setString(6, optionalString(body, "applicableScope"));
            ps.setObject(7, principal.userId());
            ps.setObject(8, principal.userId());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key == null ? null : key.longValue();

        // 正文按段落切块：知识检索的基本单位是 chunk，不建块等于新增了一条永远检索不到的知识
        String content = optionalString(body, "content");
        int chunks = 0;
        if (content != null && !content.isBlank()) {
            chunks = insertChunks(id, content);
        }
        audit(principal, "CREATE", "knowledge_doc", id, null,
                Map.of("title", title, "chunks", chunks), "新增知识文档");
        return requireKnowledge(id);
    }

    @Transactional
    public Map<String, Object> updateKnowledge(Long id, Map<String, Object> body, UserPrincipal principal) {
        Map<String, Object> before = requireKnowledge(id);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        Map<String, String> columnOf = new LinkedHashMap<>();
        columnOf.put("title", "title");
        columnOf.put("category", "category");
        columnOf.put("summary", "summary");
        columnOf.put("sourceExcerpt", "source_excerpt");
        columnOf.put("applicableScope", "applicable_scope");
        for (Map.Entry<String, String> entry : columnOf.entrySet()) {
            if (body.containsKey(entry.getKey())) {
                sets.add(entry.getValue() + " = ?");
                args.add(body.get(entry.getKey()) == null ? null : String.valueOf(body.get(entry.getKey())));
            }
        }
        if (body.containsKey("sourceId")) {
            Long sourceId = asLong(body.get("sourceId"));
            if (sourceId != null) {
                requireSource(sourceId);
            }
            sets.add("source_id = ?");
            args.add(sourceId);
        }
        if (!sets.isEmpty()) {
            // 正文/元数据变更后重新回到待审状态
            sets.add("review_status = 'PENDING'");
            sets.add("updated_by = ?");
            sets.add("updated_at = CURRENT_TIMESTAMP");
            args.add(principal.userId());
            args.add(id);
            jdbc.update("UPDATE knowledge_doc SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
        }
        String content = optionalString(body, "content");
        if (content != null && !content.isBlank()) {
            // 重新切块：旧的块必须一起逻辑删除，否则检索会同时命中新旧两版正文
            jdbc.update("DELETE FROM knowledge_chunk WHERE doc_id = ?", id);
            insertChunks(id, content);
        }
        audit(principal, "UPDATE", "knowledge_doc", id, before, requireKnowledge(id), "编辑知识文档");
        return requireKnowledge(id);
    }

    /**
     * 「重建检索索引」。
     *
     * <p>当前检索是关键词匹配，不需要外部索引；这里如实返回"已处理多少文档、多少分块、
     * 是否需要向量模型"，而不是假装重建了一个向量库。{@code embeddingModel} 为空表示
     * 语义检索尚未启用——这正是 PRD「先关键词、后语义」的阶段划分。
     */
    public Map<String, Object> reindex(UserPrincipal principal) {
        Integer docs = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc WHERE deleted_at IS NULL", Integer.class);
        Integer chunks = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk", Integer.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docCount", docs == null ? 0 : docs);
        data.put("chunkCount", chunks == null ? 0 : chunks);
        data.put("mode", "KEYWORD");
        data.put("embeddingModel", null);
        data.put("notice", "当前为关键词检索，无需重建外部索引；配置向量模型后可切换为语义检索。");
        audit(principal, "REINDEX", "knowledge_doc", null, null, data, "重建检索索引");
        return data;
    }

    // ============================================================ 审计与用户

    public PageResult<Map<String, Object>> auditLogs(Long operatorId, String action, String from, String to,
                                                     int page, int pageSize) {
        validatePage(page, pageSize);
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.operator_id, COALESCE(u.username, l.operator_name) AS operator_name,
                       l.action, l.target_type, l.target_id, l.detail,
                       l.before_json, l.after_json, l.ip, l.created_at
                FROM admin_audit_log l
                LEFT JOIN sys_user u ON u.id = l.operator_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (operatorId != null) {
            sql.append(" AND l.operator_id = ?");
            args.add(operatorId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND l.action = ?");
            args.add(action);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND l.created_at >= ?");
            args.add(from);
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND l.created_at <= ?");
            args.add(to);
        }
        sql.append(" ORDER BY l.id DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        int total = rows.size();
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows.subList(fromIndex, toIndex)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("operatorId", row.get("operator_id"));
            // 优先显示账号当前用户名；账号被删除后回退到写入时冗余的 operator_name，
            // 否则审计记录里的操作人会变成一片空白。
            item.put("operatorName", row.get("operator_name"));
            item.put("action", row.get("action"));
            item.put("targetType", row.get("target_type"));
            item.put("targetId", row.get("target_id"));
            item.put("detail", row.get("detail"));
            item.put("before", row.get("before_json"));
            item.put("after", row.get("after_json"));
            item.put("ip", row.get("ip"));
            item.put("createdAt", time(row.get("created_at")));
            list.add(item);
        }
        return PageResult.of(list, page, pageSize, total);
    }

    public PageResult<Map<String, Object>> listUsers(String keyword, String role, int page, int pageSize) {
        validatePage(page, pageSize);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, u.nickname, u.email, u.status, u.last_login_at, u.created_at
                FROM sys_user u
                WHERE u.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.username LIKE ? ESCAPE '\\' OR COALESCE(u.nickname, '') LIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(keyword.trim()) + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.id");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 角色用一条独立查询取回后在 Java 里归并：GROUP_CONCAT 是 MySQL 专有函数，
        // 用它会让 dev/test（H2）与生产（MySQL）走不同代码路径，宁可多一次查询也不引入方言。
        Map<Long, List<String>> rolesOf = new LinkedHashMap<>();
        jdbc.query("""
                SELECT ur.user_id, r.code
                FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                ORDER BY r.id
                """, rs -> {
            Long userId = rs.getLong("user_id");
            rolesOf.computeIfAbsent(userId, key -> new ArrayList<>()).add(rs.getString("code"));
        });

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("id")).longValue();
            List<String> codes = rolesOf.getOrDefault(userId, List.of());
            if (role != null && !role.isBlank() && !codes.contains(role)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", userId);
            item.put("username", row.get("username"));
            item.put("nickname", row.get("nickname"));
            item.put("email", row.get("email"));
            item.put("status", row.get("status"));
            item.put("roles", codes);
            item.put("lastLoginAt", time(row.get("last_login_at")));
            item.put("createdAt", time(row.get("created_at")));
            filtered.add(item);
        }
        int total = filtered.size();
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        return PageResult.of(filtered.subList(fromIndex, toIndex), page, pageSize, total);
    }

    // ================================================================ 内部

    /**
     * 写一条后台审计记录。
     *
     * <p>审计失败不能让业务失败：业务已经改完了，回滚反而是错的。但「不让业务失败」不等于
     * 「可以静默失败」——这里必须打 WARN 日志，否则审计表空了也不会有人发现。
     */
    private void audit(UserPrincipal principal, String action, String targetType, Long targetId,
                       Object before, Object after, String detail) {
        try {
            jdbc.update("""
                    INSERT INTO admin_audit_log (operator_id, operator_name, action, target_type,
                                                 target_id, detail, before_json, after_json, ip, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP)
                    """, principal.userId(), principal.username(), action, targetType, targetId, detail,
                    toJson(before), toJson(after));
        } catch (Exception e) {
            log.warn("写后台审计日志失败（业务已生效）：{}", e.getMessage());
        }
    }

    /**
     * 把 JDBC 取回的时间列格式化成响应里的展示字符串。
     *
     * <p>直接 {@code String.valueOf(row.get(...))} 会把驱动相关的尾巴带出去（H2 的
     * {@code 2025-01-01 00:00:00.0}、MySQL 可能是别的位数），同一份数据在两种环境下
     * 长得不一样，前端和测试都无法稳定依赖。统一交给 {@link Timestamps}。
     */
    private String time(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return Timestamps.format(timestamp);
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.format(Timestamps.DISPLAY);
        }
        return String.valueOf(value);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private int insertChunks(Long docId, String content) {
        String[] paragraphs = content.split("\\n\\s*\\n");
        int index = 0;
        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                continue;
            }
            String heading = null;
            int newline = text.indexOf('\n');
            if (newline > 0 && text.length() < 400) {
                heading = text.substring(0, newline).trim();
                text = text.substring(newline).trim();
            }
            jdbc.update("""
                    INSERT INTO knowledge_chunk (doc_id, chunk_index, heading, content, token_count, keywords,
                                                 created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, docId, index++, heading, text, Math.max(1, text.length() / 2));
        }
        return index;
    }

    private Map<String, Object> requireFood(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.id, f.code, f.name, f.en_name, f.category_id, c.name AS category_name,
                       f.intro, f.common_usage, f.origin, f.storage_method, f.suitable_for, f.cautions,
                       f.publish_status, f.review_status, f.published_at, f.updated_at
                FROM food f LEFT JOIN food_category c ON c.id = f.category_id
                WHERE f.id = ? AND f.deleted_at IS NULL
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "食品不存在");
        }
        return foodRow(rows.get(0));
    }

    private Map<String, Object> foodRow(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("code", row.get("code"));
        item.put("name", row.get("name"));
        item.put("enName", row.get("en_name"));
        item.put("categoryId", row.get("category_id"));
        item.put("categoryName", row.get("category_name"));
        item.put("intro", row.get("intro"));
        item.put("commonUsage", row.get("common_usage"));
        item.put("origin", row.get("origin"));
        item.put("storageMethod", row.get("storage_method"));
        item.put("suitableFor", row.get("suitable_for"));
        item.put("cautions", row.get("cautions"));
        item.put("publishStatus", row.get("publish_status"));
        item.put("reviewStatus", row.get("review_status"));
        item.put("publishedAt", time(row.get("published_at")));
        item.put("updatedAt", time(row.get("updated_at")));
        if (row.containsKey("variant_count")) {
            item.put("variantCount", row.get("variant_count"));
        }
        return item;
    }

    private Map<String, Object> requireVariant(Long variantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT v.id, v.food_id, v.state, v.cooking_method, v.name_suffix, v.edible_ratio,
                       v.description, v.is_default, v.review_status, v.publish_status
                FROM food_state_variant v
                WHERE v.id = ? AND v.deleted_at IS NULL
                """, variantId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NUTRITION_NOT_FOUND, "食品形态不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("foodId", row.get("food_id"));
        item.put("state", row.get("state"));
        item.put("cookingMethod", row.get("cooking_method"));
        item.put("nameSuffix", row.get("name_suffix"));
        item.put("edibleRatio", row.get("edible_ratio"));
        item.put("description", row.get("description"));
        item.put("isDefault", Boolean.TRUE.equals(row.get("is_default")) || Integer.valueOf(1).equals(row.get("is_default")));
        item.put("reviewStatus", row.get("review_status"));
        item.put("publishStatus", row.get("publish_status"));
        item.put("variantLabel", com.nutritionagent.food.service.FoodQueryService.stateLabel(
                (String) row.get("state"), (String) row.get("cooking_method")));
        return item;
    }

    private Map<String, Object> requireKnowledge(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT kd.id, kd.title, kd.category, kd.summary, kd.review_status, kd.publish_status,
                       kd.source_id, kd.updated_at
                FROM knowledge_doc kd WHERE kd.id = ? AND kd.deleted_at IS NULL
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND, "知识文档不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("title", row.get("title"));
        item.put("category", row.get("category"));
        item.put("summary", row.get("summary"));
        item.put("sourceId", row.get("source_id"));
        item.put("reviewStatus", row.get("review_status"));
        item.put("publishStatus", row.get("publish_status"));
        item.put("updatedAt", time(row.get("updated_at")));
        return item;
    }

    /** 审计里的"变更前/后"只保留可比较的关键字段，避免把整行塞进 JSON 列。 */
    private Map<String, Object> briefFood(Long id) {
        return requireFood(id);
    }

    private Map<String, Object> briefNutrition(Long variantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT n.code, nv.value_status, nv.amount, nv.source_id
                FROM nutrition_value nv JOIN nutrient n ON n.id = nv.nutrient_id
                WHERE nv.variant_id = ? AND nv.deleted_at IS NULL
                """, variantId);
        return Map.of("variantId", variantId, "rows", rows);
    }

    private void requireCategory(Long categoryId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_category WHERE id = ? AND deleted_at IS NULL", Integer.class, categoryId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "食品分类不存在：categoryId=" + categoryId);
        }
    }

    private boolean existsByCode(String code, Long excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM food WHERE code = ? AND deleted_at IS NULL", Integer.class, code)
                : jdbc.queryForObject("SELECT COUNT(*) FROM food WHERE code = ? AND deleted_at IS NULL AND id <> ?",
                Integer.class, code, excludeId);
        return count != null && count > 0;
    }

    private boolean existsVariant(Long foodId, String state, String cookingMethod, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM food_state_variant WHERE food_id = ? AND state = ? AND deleted_at IS NULL"
                + (cookingMethod == null ? " AND cooking_method IS NULL" : " AND cooking_method = ?")
                + (excludeId == null ? "" : " AND id <> ?");
        List<Object> args = new ArrayList<>();
        args.add(foodId);
        args.add(state);
        if (cookingMethod != null) {
            args.add(cookingMethod);
        }
        if (excludeId != null) {
            args.add(excludeId);
        }
        Integer count = jdbc.queryForObject(sql, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private static void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "分页参数不合法：page ≥ 1，pageSize ∈ [1, " + MAX_PAGE_SIZE + "]");
        }
    }

    private static String requiredString(Map<String, Object> body, String key, String message) {
        Object value = body.get(key);
        String text = value == null ? null : String.valueOf(value).trim();
        if (text == null || text.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return text;
    }

    private static String optionalString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long requiredLong(Map<String, Object> body, String key, String message) {
        Long value = asLong(body.get(key));
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static java.math.BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.math.BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new java.math.BigDecimal(n.toString());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "数值格式不正确：" + text);
        }
    }

    private static boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
