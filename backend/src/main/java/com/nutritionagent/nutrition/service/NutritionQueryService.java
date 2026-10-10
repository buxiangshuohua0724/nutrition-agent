package com.nutritionagent.nutrition.service;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.db.Timestamps;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.food.service.FoodQueryService;
import com.nutritionagent.nutrition.dto.NutritionCalculation;
import com.nutritionagent.nutrition.dto.NutritionTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养域的读取与折算（FR-004）。
 *
 * <p>职责边界：本类负责"从库里取到确定的事实行"，算术全部交给 {@link NutritionMath}
 * 这个纯函数。**本类不含任何模型调用**，因此模型不可用时营养查询必须照常可用（PRD 6.6）。
 */
@Service
public class NutritionQueryService {

    private final JdbcTemplate jdbc;
    private final FoodQueryService foodQueryService;

    public NutritionQueryService(JdbcTemplate jdbc, FoodQueryService foodQueryService) {
        this.jdbc = jdbc;
        this.foodQueryService = foodQueryService;
    }

    /** 营养素字典（契约 {@code GET /nutrients}），按 display_order 升序。 */
    public List<NutrientView> listNutrients() {
        return jdbc.query(
                "SELECT id, code, name, unit, group_code, display_order FROM nutrient "
                        + "WHERE enabled = 1 ORDER BY display_order, id",
                (rs, rowNum) -> new NutrientView(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getString("group_code"),
                        rs.getInt("display_order")));
    }

    /**
     * 营养数据表（契约 {@code GET /foods/{id}/nutrition}）。
     *
     * @param variantId 形态 id，为空时取该食品的默认形态
     * @throws BusinessException 食品不存在 → 40401；形态不属于该食品或没有营养数据 → 40402
     */
    public NutritionTable nutritionTable(Long foodId, Long variantId) {
        Long resolved = resolveVariant(foodId, variantId);
        return buildTable(resolved);
    }

    /**
     * 按重量折算（契约 {@code POST /nutrition/calculate}）。
     *
     * <p>先校验输入重量与单位（非法 → 40002 / 40001），再解析形态，
     * 最后逐项用 {@link NutritionMath} 折算。缺失项不进 {@code items}，只进 {@code missingNutrients}。
     */
    public NutritionCalculation calculate(Long foodId, Long variantId, BigDecimal weight, String weightUnit) {
        BigDecimal grams = NutritionMath.toGrams(weight, weightUnit);
        Long resolved = resolveVariant(foodId, variantId);
        NutritionTable table = buildTable(resolved);

        List<NutritionCalculation.Item> items = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<Long, NutritionTable.Source> sourceById = new LinkedHashMap<>();

        for (NutritionTable.Row row : table.rows()) {
            if ("MISSING".equals(row.valueStatus()) || row.amount() == null) {
                // 缺失不是 0：不进 items，也不假装它是 0 参与求和
                missing.add(row.nutrientName());
                continue;
            }
            BigDecimal computed = NutritionMath.compute(row.amount(), table.referenceAmount(), grams);
            items.add(new NutritionCalculation.Item(
                    row.nutrientId(),
                    row.nutrientCode(),
                    row.nutrientName(),
                    row.unit(),
                    row.amount(),
                    computed,
                    NutritionMath.displayText(computed),
                    NutritionMath.formula(row.amount(), row.unit(), grams,
                            table.referenceAmount(), table.referenceUnit(), computed)));
            if (row.source() != null) {
                sourceById.putIfAbsent(row.source().id(), row.source());
            }
        }

        return new NutritionCalculation(
                table.foodId(), table.foodName(), table.variantId(), table.variantLabel(),
                weight, weightUnit == null ? "g" : weightUnit.trim().toLowerCase(java.util.Locale.ROOT),
                table.referenceAmount(), table.referenceUnit(),
                items, true,
                "基于每 " + NutritionMath.displayText(table.referenceAmount()) + table.referenceUnit()
                        + " 参考值的线性折算，未考虑烹饪损失",
                missing,
                new ArrayList<>(sourceById.values()));
    }

    /** 解析并校验形态归属：为空取默认形态；不属于该食品一律 40402（不泄露别的食品的形态是否存在）。 */
    private Long resolveVariant(Long foodId, Long variantId) {
        if (foodId == null) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "foodId 不能为空");
        }
        // 先确认食品可见，避免对不存在的食品报"形态没有营养数据"这种误导性错误
        if (foodQueryService.detail(foodId) == null) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "食品不存在或未发布：" + foodId);
        }
        if (variantId != null) {
            if (!foodQueryService.variantBelongsTo(variantId, foodId)) {
                throw new BusinessException(ErrorCode.NUTRITION_NOT_FOUND,
                        "形态 " + variantId + " 不属于食品 " + foodId);
            }
            return variantId;
        }
        Long fallback = foodQueryService.defaultVariantId(foodId);
        if (fallback == null) {
            throw new BusinessException(ErrorCode.NUTRITION_NOT_FOUND, "食品 " + foodId + " 还没有可用的形态");
        }
        return fallback;
    }

    /** 组装营养表。表头（参考重量）取该形态下营养行的值；一行都没有时按 40402 处理。 */
    private NutritionTable buildTable(Long variantId) {
        List<NutritionTable.Row> rows = jdbc.query(
                "SELECT nv.id, nv.nutrient_id, nv.reference_amount, nv.reference_unit, nv.reference_note, "
                        + "  nv.value_status, nv.amount, nv.source_id, nv.source_detail, "
                        + "  n.id AS n_id, n.code AS n_code, n.name AS n_name, n.unit AS n_unit, "
                        + "  n.group_code AS n_group, n.display_order AS n_order, "
                        + "  s.id AS s_id, s.name AS s_name, s.code AS s_code, s.source_type AS s_type, "
                        + "  s.applicable_scope AS s_scope "
                        + "FROM nutrition_value nv "
                        + "JOIN nutrient n ON n.id = nv.nutrient_id "
                        + "JOIN data_source s ON s.id = nv.source_id "
                        + "WHERE nv.variant_id = ? AND nv.deleted_at IS NULL "
                        + "ORDER BY n.display_order, n.id",
                (rs, rowNum) -> {
                    String status = rs.getString("value_status");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    // 防御式归一：库里若出现自相矛盾的行（例如 MISSING 却带数值），
                    // 一律按"缺失"对外呈现，绝不把可疑数值当事实输出
                    boolean missing = "MISSING".equals(status) || amount == null;
                    String valueStatus = missing ? "MISSING" : status;
                    BigDecimal safeAmount = missing ? null : amount;
                    String display = missing ? "暂无数据" : NutritionMath.displayText(safeAmount);
                    return new NutritionTable.Row(
                            rs.getLong("nutrient_id"),
                            rs.getString("n_code"),
                            rs.getString("n_name"),
                            rs.getString("n_group"),
                            rs.getInt("n_order"),
                            valueStatus,
                            rs.getString("n_unit"),
                            safeAmount,
                            display,
                            new NutritionTable.Source(
                                    rs.getLong("s_id"),
                                    rs.getString("s_name"),
                                    rs.getString("s_code"),
                                    rs.getString("s_type"),
                                    rs.getString("s_scope")));
                },
                variantId);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NUTRITION_NOT_FOUND,
                    "形态 " + variantId + " 还没有营养数据");
        }

        BigDecimal referenceAmount = jdbc.queryForObject(
                "SELECT reference_amount FROM nutrition_value WHERE variant_id = ? AND deleted_at IS NULL "
                        + "ORDER BY id LIMIT 1",
                BigDecimal.class, variantId);
        String referenceUnit = jdbc.queryForObject(
                "SELECT reference_unit FROM nutrition_value WHERE variant_id = ? AND deleted_at IS NULL "
                        + "ORDER BY id LIMIT 1",
                String.class, variantId);
        String referenceNote = jdbc.queryForObject(
                "SELECT COALESCE(reference_note, '') FROM nutrition_value WHERE variant_id = ? AND deleted_at IS NULL "
                        + "ORDER BY id LIMIT 1",
                String.class, variantId);

        // 库里可能整行缺失（种子数据就有 7 行缺失）。缺的行必须显式补成「暂无数据」，
        // 否则界面会显示一个少了几项的表格，看起来像是"这个食品没有这项营养素"而不是"数据没录"。
        rows = fillAbsentNutrients(rows, referenceAmount, referenceUnit, referenceNote);

        FoodQueryService.VariantLabel label = foodQueryService.variantLabel(variantId);
        String foodName = label == null ? null : label.foodName();
        String variantLabel = label == null ? null
                : FoodQueryService.stateLabel(label.state(), label.cookingMethod());

        // 营养数据的更新时间取该形态下所有营养行的最大值：管理员改的可能是任意一项，
        // 「整表数据最后被动过是什么时候」才是用户需要知道的。
        String dataUpdatedAt = Timestamps.format(jdbc.queryForObject(
                "SELECT MAX(updated_at) FROM nutrition_value WHERE variant_id = ? AND deleted_at IS NULL",
                java.sql.Timestamp.class, variantId));

        return new NutritionTable(variantId, label == null ? null : label.foodId(), foodName, variantLabel,
                referenceAmount, referenceUnit,
                referenceNote == null || referenceNote.isBlank() ? null : referenceNote,
                rows, dataUpdatedAt);
    }

    /** 把字典里存在、但该形态一条记录都没有的营养素补成 MISSING 行。 */
    private List<NutritionTable.Row> fillAbsentNutrients(List<NutritionTable.Row> rows,
                                                         BigDecimal referenceAmount, String referenceUnit,
                                                         String referenceNote) {
        List<NutrientView> dictionary = listNutrients();
        if (dictionary.size() <= rows.size()) {
            return rows;
        }
        java.util.Set<Long> present = new java.util.HashSet<>();
        rows.forEach(row -> present.add(row.nutrientId()));

        // 补齐行的来源：沿用该形态已有的来源，绝不凭空造一个来源出来
        NutritionTable.Source fallbackSource = rows.isEmpty() ? null : rows.get(0).source();

        List<NutritionTable.Row> merged = new ArrayList<>(rows);
        for (NutrientView nutrient : dictionary) {
            if (present.contains(nutrient.id())) {
                continue;
            }
            merged.add(new NutritionTable.Row(
                    nutrient.id(), nutrient.code(), nutrient.name(), nutrient.groupCode(),
                    nutrient.displayOrder(), "MISSING", nutrient.unit(), null, "暂无数据", fallbackSource));
        }
        merged.sort(java.util.Comparator
                .comparing(NutritionTable.Row::displayOrder, java.util.Comparator.nullsLast(Integer::compareTo))
                .thenComparing(NutritionTable.Row::nutrientId));
        return merged;
    }

    /** 营养素字典项。 */
    public record NutrientView(Long id, String code, String name, String unit, String groupCode,
                               Integer displayOrder) {
    }
}
