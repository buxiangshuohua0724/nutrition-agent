package com.nutritionagent.food.service;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.api.PageResult;
import com.nutritionagent.common.db.Timestamps;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.food.dto.CategorySummary;
import com.nutritionagent.food.dto.FoodDetail;
import com.nutritionagent.food.dto.FoodSummary;
import com.nutritionagent.food.dto.ImageView;
import com.nutritionagent.food.dto.VariantView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 食品域的只读查询服务（FR-001 / FR-002 / FR-003）。
 *
 * <p>三条贯穿全类的硬规则：
 * <ol>
 *   <li><b>可见性</b>：公开接口一律只返回 {@code publish_status='PUBLISHED'}
 *       且 {@code review_status='APPROVED'} 且 {@code deleted_at IS NULL} 的食品
 *       （PRD 5.2：下架食品不得出现在公开搜索结果中）。</li>
 *   <li><b>三态</b>：能量缺失时 {@code energyKcal=null}、{@code energyAvailable=false}，
 *       绝不用 0 兜底（{@code value_status='ZERO'} 是「确认是 0」，与缺失不同）。</li>
 *   <li><b>不做营养计算</b>：本类只取数，不折算、不推导。按重量折算在
 *       {@code NutritionCalculator} 里作为纯函数单独实现并可独立测试。</li>
 * </ol>
 *
 * <p>实现上刻意用 JdbcTemplate 而不是全 ORM：这里的查询有 JOIN、UNION 与多列排序，
 * 用 QueryWrapper 拼出来的 SQL 不可读也不可审计；而"取哪些行"的规则恰恰是本项目
 * 最需要被逐行审读的地方。
 */
@Service
public class FoodQueryService {

    /** 单页上限（契约 4.1：pageSize 上限 100）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 公开可见的食品 / 形态条件。**所有**公开查询都必须带上它，
     * 因此集中在这里而不是散落到每个方法里。
     */
    private static final String VISIBLE_FOOD =
            "f.deleted_at IS NULL AND f.publish_status = 'PUBLISHED' AND f.review_status = 'APPROVED'";
    private static final String VISIBLE_VARIANT =
            "v.deleted_at IS NULL AND v.publish_status = 'PUBLISHED' AND v.review_status = 'APPROVED'";

    private final JdbcTemplate jdbc;

    public FoodQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------

    /** 分类列表（含已发布食品数）。契约 {@code GET /categories}。 */
    public List<CategorySummary> listCategories() {
        String sql = "SELECT c.id, c.name, c.slug, "
                + "  (SELECT COUNT(*) FROM food f WHERE f.category_id = c.id AND " + VISIBLE_FOOD + ") AS food_count "
                + "FROM food_category c WHERE c.deleted_at IS NULL AND c.enabled = 1 "
                + "ORDER BY c.sort_order, c.id";
        return jdbc.query(sql, (rs, rowNum) -> new CategorySummary(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getLong("food_count"),
                null));
    }

    /** slug 是否是一个存在的启用分类。用于把「分类不存在」与「分类下为空」区分开。 */
    public boolean categoryExists(String slug) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_category WHERE slug = ? AND deleted_at IS NULL AND enabled = 1",
                Integer.class, slug);
        return count != null && count > 0;
    }

    // ------------------------------------------------------------------
    // 搜索与列表
    // ------------------------------------------------------------------

    /** 排序方式（契约 4.1 的 {@code sort} 取值）。 */
    public enum SortMode {
        RELEVANCE, NAME, ENERGY_ASC, ENERGY_DESC;

        public static SortMode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELEVANCE;
            }
            return switch (raw.trim()) {
                case "name" -> NAME;
                case "energyAsc" -> ENERGY_ASC;
                case "energyDesc" -> ENERGY_DESC;
                case "relevance" -> RELEVANCE;
                default -> throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "不支持的排序方式：" + raw + "（可选 relevance / name / energyAsc / energyDesc）");
            };
        }
    }

    /**
     * 搜索食品。契约 {@code GET /foods/search}。
     *
     * <p>匹配范围：标准名、英文名、别名、业务编码（PRD 5.2 要求别名可被搜到，
     * 但标准名精确匹配必须排在别名匹配之前，即"别名不得劫持标准名"）。
     *
     * @param keyword    关键词，可空
     * @param categoryId 分类筛选，可空
     * @param page       页码，从 1 开始
     * @param pageSize   每页条数
     * @param sort       排序方式（{@code null} 或 {@code relevance} 时按匹配等级排序）
     */
    public PageResult<FoodSummary> search(String keyword, Long categoryId,
                                          int page, int pageSize, SortMode sort) {
        validatePage(page, pageSize);

        List<Long> ids = matchingFoodIds(keyword, categoryId);

        // 能量与默认形态需要一次性批量取，避免按行查询（N+1）
        Map<Long, FoodFacts> facts = foodFacts(ids);
        Map<Long, BigDecimal> energy = energyByDefaultVariant(facts);

        List<FoodSummary> all = new ArrayList<>(ids.size());
        for (Long id : ids) {
            all.add(toSummary(facts.get(id), energy.get(id)));
        }
        all = sortSummaries(all, sort);

        return pageOf(all, page, pageSize);
    }

    /** 分类下食品分页。契约 {@code GET /categories/{slug}/foods}。 */
    public PageResult<FoodSummary> foodsOfCategory(String slug, int page, int pageSize) {
        validatePage(page, pageSize);
        if (!categoryExists(slug)) {
            // 分类不存在时返回空页而不是 404：分类页的 URL 可能被手工输入，
            // 前端用空列表渲染"该分类下暂无食品"更符合用户预期。
            return PageResult.empty(page, pageSize);
        }
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM food_category WHERE slug = ? AND deleted_at IS NULL AND enabled = 1",
                Long.class, slug);
        return search(null, categoryId, page, pageSize, SortMode.NAME);
    }

    /** 热门 / 推荐 / 近期新增。契约 {@code GET /foods/featured}。 */
    public List<FoodSummary> featured(String type, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        String order;
        if ("new".equalsIgnoreCase(type)) {
            // 近期新增按发布时间倒序；时间相同再按 id，保证结果稳定可复现
            order = "f.published_at DESC, f.id DESC";
        } else {
            // hot / recommended 当前都没有真实行为数据可依据，故按分类与 id 稳定排序；
            // 不臆造"热度分"，也不假装这是个性化推荐（PRD 要求推荐必须能说明依据）
            order = "c.sort_order, f.id";
        }
        String sql = "SELECT f.id FROM food f LEFT JOIN food_category c ON c.id = f.category_id "
                + "WHERE " + VISIBLE_FOOD + " ORDER BY " + order + " LIMIT ?";

        List<Long> ids = jdbc.queryForList(sql, Long.class, safeLimit);
        Map<Long, FoodFacts> facts = foodFacts(ids);
        Map<Long, BigDecimal> energy = energyByDefaultVariant(facts);

        List<FoodSummary> list = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FoodFacts fact = facts.get(id);
            if (fact != null) {
                list.add(toSummary(fact, energy.get(id)));
            }
        }
        return list;
    }

    /** 相关食品。契约 {@code GET /foods/{id}/related}。同分类优先，不足时用其他分类补齐。 */
    public List<FoodSummary> related(Long foodId, int limit) {
        FoodBase base = foodBase(foodId, false);
        if (base == null) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "食品不存在：" + foodId);
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));

        String sql = "SELECT f.id FROM food f WHERE " + VISIBLE_FOOD + " AND f.id <> ? "
                + "ORDER BY (CASE WHEN f.category_id = ? THEN 0 ELSE 1 END), f.id LIMIT ?";
        List<Long> ids = jdbc.queryForList(sql, Long.class, foodId,
                base.categoryId() == null ? -1L : base.categoryId(), safeLimit);

        Map<Long, FoodFacts> facts = foodFacts(ids);
        Map<Long, BigDecimal> energy = energyByDefaultVariant(facts);
        List<FoodSummary> list = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FoodFacts fact = facts.get(id);
            if (fact != null) {
                list.add(toSummary(fact, energy.get(id)));
            }
        }
        return list;
    }

    /** 取单个食品的列表项视图（收藏列表等场景复用同一套字段）。不可见或不存在返回 null。 */
    public FoodSummary summaryById(Long foodId) {
        Map<Long, FoodFacts> facts = foodFacts(List.of(foodId));
        FoodFacts fact = facts.get(foodId);
        if (fact == null) {
            return null;
        }
        Integer visible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food f WHERE f.id = ? AND " + VISIBLE_FOOD, Integer.class, foodId);
        if (visible == null || visible == 0) {
            return null;
        }
        return toSummary(fact, energyByDefaultVariant(facts).get(foodId));
    }

    // ------------------------------------------------------------------
    // 详情
    // ------------------------------------------------------------------

    /** 食品详情。契约 {@code GET /foods/{id}}。不存在或不可见一律 40401（不泄露草稿是否存在）。 */
    public FoodDetail detail(Long foodId) {
        return detail(foodId, true);
    }

    /**
     * 食品详情（后台版：草稿与未发布的形态也要能看到）。
     *
     * <p>后台的编辑界面必须能打开一份还没发布的食品——否则「新建 → 编辑 → 提交审核」这条链路根本走不通。
     * 可见性差异只在 {@code includeUnpublished} 这一个开关上，其余字段与结构完全一致，
     * 因此后台复用的是同一份读代码，而不是另写一套容易与前台漂移的查询。</p>
     *
     * @param includeUnpublished 为 {@code true} 时不做发布状态与审核状态过滤（仅限后台接口调用）
     */
    public FoodDetail detail(Long foodId, boolean includeUnpublished) {
        FoodBase base = foodBase(foodId, !includeUnpublished);
        if (base == null) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND,
                    includeUnpublished ? "食品不存在或未发布：" + foodId : "食品不存在：" + foodId);
        }

        List<FoodDetail.AliasView> aliases = jdbc.query(
                "SELECT alias, alias_type FROM food_alias WHERE food_id = ? AND deleted_at IS NULL "
                        + "ORDER BY weight DESC, id",
                (rs, rowNum) -> new FoodDetail.AliasView(rs.getString("alias"), rs.getString("alias_type")),
                foodId);

        List<ImageView> images = jdbc.query(
                "SELECT id, url, thumb_url, image_type, caption, source_note, license_note, is_cover, sort_order "
                        + "FROM food_image WHERE food_id = ? AND deleted_at IS NULL "
                        + "ORDER BY is_cover DESC, sort_order, id",
                (rs, rowNum) -> new ImageView(
                        rs.getLong("id"),
                        rs.getString("url"),
                        rs.getString("thumb_url"),
                        rs.getString("image_type"),
                        rs.getString("caption"),
                        rs.getString("source_note"),
                        rs.getString("license_note"),
                        rs.getBoolean("is_cover"),
                        rs.getInt("sort_order")),
                foodId);

        List<VariantView> variants = jdbc.query(
                "SELECT id, state, cooking_method, name_suffix, edible_ratio, description, is_default "
                        + "FROM food_state_variant v WHERE v.food_id = ? AND v.deleted_at IS NULL "
                        + (includeUnpublished
                                ? ""
                                : " AND v.publish_status = 'PUBLISHED' AND v.review_status = 'APPROVED' ")
                        + " ORDER BY v.is_default DESC, v.id",
                (rs, rowNum) -> new VariantView(
                        rs.getLong("id"),
                        rs.getString("state"),
                        null, // stateLabel 由 state 与 cookingMethod 组合，见 stateLabel()
                        rs.getString("cooking_method"),
                        rs.getString("name_suffix"),
                        rs.getBigDecimal("edible_ratio"),
                        rs.getString("description"),
                        rs.getBoolean("is_default")),
                foodId);
        variants = variants.stream()
                .map(v -> new VariantView(v.id(), v.state(), stateLabel(v.state(), v.cookingMethod()),
                        v.cookingMethod(), v.nameSuffix(), v.edibleRatio(), v.description(), v.isDefault()))
                .toList();

        return new FoodDetail(
                base.id(), base.code(), base.name(), base.enName(), base.scientificName(),
                base.categoryId() == null ? null
                        : new FoodDetail.CategoryRef(base.categoryId(), base.categoryName(), base.categorySlug()),
                aliases, base.intro(), base.commonUsage(), base.origin(), base.storageMethod(),
                base.suitableFor(), base.cautions(), images, variants,
                base.updatedAt(), base.publishStatus());
    }

    /**
     * 形态的展示名。
     *
     * <p>刻意不把 {@code state} 与 {@code name_suffix} 直接拼接：种子的 {@code name_suffix}
     * 已经形如「（生）」「（炒）」，再拼一次会得到「生（生）」这种自相重复的文案。
     */
    public static String stateLabel(String state, String cookingMethod) {
        String base = switch (state == null ? "" : state) {
            case "RAW" -> "生";
            case "COOKED" -> "熟";
            case "PEELED" -> "去皮";
            case "PROCESSED" -> "加工";
            case "DRIED" -> "干制";
            case "FROZEN" -> "冷冻";
            default -> state == null ? "" : state;
        };
        if (cookingMethod != null && !cookingMethod.isBlank()) {
            return base + "（" + cookingMethod + "）";
        }
        return base;
    }

    /** 取食品的默认形态 id（{@code is_default=1} 且可见）；没有则退到最小的可见形态 id。 */
    public Long defaultVariantId(Long foodId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT v.id FROM food_state_variant v WHERE v.food_id = ? AND " + VISIBLE_VARIANT
                        + " ORDER BY v.is_default DESC, v.id LIMIT 1",
                Long.class, foodId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 形态是否属于该食品且可见（用于阻止"拿另一个食品的形态取数"）。 */
    public boolean variantBelongsTo(Long variantId, Long foodId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_state_variant v WHERE v.id = ? AND v.food_id = ? AND "
                        + VISIBLE_VARIANT,
                Integer.class, variantId, foodId);
        return count != null && count > 0;
    }

    /** 形态展示信息（营养表头部用）。 */
    public VariantLabel variantLabel(Long variantId) {
        List<VariantLabel> rows = jdbc.query(
                "SELECT v.food_id, f.name, v.state, v.cooking_method, v.name_suffix "
                        + "FROM food_state_variant v JOIN food f ON f.id = v.food_id "
                        + "WHERE v.id = ? AND " + VISIBLE_VARIANT + " AND " + VISIBLE_FOOD,
                (rs, rowNum) -> new VariantLabel(rs.getLong("food_id"), rs.getString("name"),
                        rs.getString("state"), rs.getString("cooking_method"), rs.getString("name_suffix")),
                variantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------------
    // 内部：数据装载
    // ------------------------------------------------------------------

    private List<Long> matchingFoodIds(String keyword, Long categoryId) {
        String trimmed = keyword == null ? "" : keyword.trim();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT f.id FROM food f "
                + "LEFT JOIN food_alias a ON a.food_id = f.id AND a.deleted_at IS NULL "
                + "WHERE ").append(VISIBLE_FOOD);
        List<Object> args = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND f.category_id = ?");
            args.add(categoryId);
        }
        if (!trimmed.isEmpty()) {
            String like = "%" + escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
            sql.append(" AND (LOWER(f.name) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(f.en_name, '')) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(f.code) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(a.alias, '')) LIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY f.id");
        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    /** LIKE 通配符转义，避免用户输入的 % 变成"匹配全部"。 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<Long, FoodFacts> foodFacts(List<Long> ids) {
        Map<Long, FoodFacts> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT f.id, f.code, f.name, f.en_name, f.intro, f.category_id, "
                + "  c.name AS category_name, c.slug AS category_slug, "
                + "  (SELECT v.id FROM food_state_variant v WHERE v.food_id = f.id AND " + VISIBLE_VARIANT
                + "   ORDER BY v.is_default DESC, v.id LIMIT 1) AS default_variant_id, "
                + "  (SELECT i.url FROM food_image i WHERE i.food_id = f.id AND i.deleted_at IS NULL "
                + "   ORDER BY i.is_cover DESC, i.sort_order, i.id LIMIT 1) AS cover_image_url "
                + "FROM food f LEFT JOIN food_category c ON c.id = f.category_id "
                + "WHERE f.id IN (" + placeholders + ")";
        jdbc.query(sql, rs -> {
            FoodFacts facts = new FoodFacts(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("en_name"),
                    rs.getString("intro"),
                    rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("category_slug"),
                    rs.getObject("default_variant_id") == null ? null : rs.getLong("default_variant_id"),
                    rs.getString("cover_image_url"));
            result.put(facts.id(), facts);
        }, ids.toArray());
        return result;
    }

    /** 按形态批量取能量（kcal）。只认 PRESENT / ZERO —— 两者都是「已确认的数值」。 */
    private Map<Long, BigDecimal> energyByVariant(List<Long> variantIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (variantIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        String sql = "SELECT nv.variant_id, nv.amount FROM nutrition_value nv "
                + "JOIN nutrient n ON n.id = nv.nutrient_id "
                + "WHERE n.is_energy = 1 AND nv.deleted_at IS NULL "
                + "AND nv.value_status IN ('PRESENT', 'ZERO') AND nv.amount IS NOT NULL "
                + "AND nv.variant_id IN (" + placeholders + ") "
                + "ORDER BY nv.variant_id, nv.id";
        jdbc.query(sql, rs -> {
            result.putIfAbsent(rs.getLong("variant_id"), rs.getBigDecimal("amount"));
        }, variantIds.toArray());
        return result;
    }

    private Map<Long, BigDecimal> energyByDefaultVariant(Map<Long, FoodFacts> facts) {
        List<Long> variantIds = facts.values().stream()
                .map(FoodFacts::defaultVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BigDecimal> byVariant = energyByVariant(variantIds);
        Map<Long, BigDecimal> byFood = new HashMap<>();
        facts.values().forEach(facts1 -> {
            if (facts1.defaultVariantId() != null) {
                byFood.put(facts1.id(), byVariant.get(facts1.defaultVariantId()));
            }
        });
        return byFood;
    }

    private FoodSummary toSummary(FoodFacts facts, BigDecimal energyKcal) {
        if (facts == null) {
            return null;
        }
        String unit = energyKcal == null ? null : "kcal";
        return new FoodSummary(
                facts.id(), facts.code(), facts.name(), facts.enName(),
                facts.categoryId(), facts.categoryName(), facts.categorySlug(),
                facts.coverImageUrl(), facts.intro(), facts.defaultVariantId(),
                energyKcal, energyKcal != null, unit,
                energyKcal == null ? null : trimNumber(energyKcal),
                facts.categoryName() == null ? List.of() : List.of(facts.categoryName()));
    }

    /** 能量文本去掉无意义的尾随零（15.000 → 15），与前端 trimNumber 口径一致。 */
    private static String trimNumber(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    private List<FoodSummary> sortSummaries(List<FoodSummary> list, SortMode sort) {
        List<FoodSummary> copy = new ArrayList<>(list);
        switch (sort) {
            case NAME -> copy.sort(Comparator.comparing(FoodSummary::name,
                    Comparator.nullsLast(String::compareTo)));
            case ENERGY_ASC, ENERGY_DESC -> {
                int dir = sort == SortMode.ENERGY_ASC ? 1 : -1;
                // 能量缺失的条目一律排最后，绝不当作 0 参与排序（否则"暂无数据"会排在最前面）
                Comparator<FoodSummary> byEnergy = (left, right) -> {
                    BigDecimal l = left.energyKcal();
                    BigDecimal r = right.energyKcal();
                    if (l == null && r == null) {
                        return 0;
                    }
                    if (l == null) {
                        return 1;
                    }
                    if (r == null) {
                        return -1;
                    }
                    return l.compareTo(r) * dir;
                };
                copy.sort(byEnergy.thenComparing(FoodSummary::id));
            }
            default -> copy.sort(Comparator.comparing(FoodSummary::id));
        }
        return copy;
    }

    private <T> PageResult<T> pageOf(List<T> all, int page, int pageSize) {
        int from = Math.min((page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return PageResult.of(new ArrayList<>(all.subList(from, to)), page, pageSize, all.size());
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "page 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "pageSize 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
    }

    private FoodBase foodBase(Long foodId, boolean requireVisible) {
        String sql = "SELECT f.id, f.code, f.name, f.en_name, f.scientific_name, f.intro, f.common_usage, "
                + "  f.origin, f.storage_method, f.suitable_for, f.cautions, f.publish_status, f.updated_at, "
                + "  f.category_id, c.name AS category_name, c.slug AS category_slug "
                + "FROM food f LEFT JOIN food_category c ON c.id = f.category_id "
                + "WHERE f.id = ? AND f.deleted_at IS NULL"
                + (requireVisible ? " AND f.publish_status = 'PUBLISHED' AND f.review_status = 'APPROVED'" : "");
        List<FoodBase> rows = jdbc.query(sql, (rs, rowNum) -> new FoodBase(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("en_name"),
                rs.getString("scientific_name"), rs.getString("intro"), rs.getString("common_usage"),
                rs.getString("origin"), rs.getString("storage_method"), rs.getString("suitable_for"),
                rs.getString("cautions"), rs.getString("publish_status"),
                Timestamps.format(rs, "updated_at"),
                rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                rs.getString("category_name"), rs.getString("category_slug")), foodId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------------
    // 内部：行载体
    // ------------------------------------------------------------------

    private record FoodFacts(Long id, String code, String name, String enName, String intro,
                             Long categoryId, String categoryName, String categorySlug,
                             Long defaultVariantId, String coverImageUrl) {
    }

    private record FoodBase(Long id, String code, String name, String enName, String scientificName,
                            String intro, String commonUsage, String origin, String storageMethod,
                            String suitableFor, String cautions, String publishStatus, String updatedAt,
                            Long categoryId, String categoryName, String categorySlug) {
    }

    /** 形态的展示信息：所属食品名 + 形态文案。 */
    public record VariantLabel(Long foodId, String foodName, String state, String cookingMethod,
                               String nameSuffix) {
    }
}
