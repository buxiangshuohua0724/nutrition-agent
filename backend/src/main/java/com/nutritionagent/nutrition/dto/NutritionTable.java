package com.nutritionagent.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营养数据表 —— 契约 3.3 {@code NutritionTable}。
 *
 * <p>核心不变量（PRD [D2]，全项目最重要的一条）：
 * <ul>
 *   <li>{@code value_status='PRESENT'} → {@code amount} 非空</li>
 *   <li>{@code value_status='ZERO'} → {@code amount}=0，{@code displayText}="0"</li>
 *   <li>{@code value_status='MISSING'} → {@code amount}=<b>null</b>、{@code displayText}="暂无数据"</li>
 * </ul>
 * <b>缺失绝不等于 0</b>，任何层都不许用 0 兜底。
 */
public record NutritionTable(
        Long variantId,
        Long foodId,
        String foodName,
        String variantLabel,
        BigDecimal referenceAmount,
        String referenceUnit,
        String referenceNote,
        List<Row> rows,
        /**
         * 营养数据的更新时间（{@code yyyy-MM-dd HH:mm:ss}），取自该形态下
         * {@code nutrition_value.updated_at} 的最大值。
         *
         * <p>为什么不复用 {@code FoodDetail.dataUpdatedAt}：后者是**食品档案**的更新时间，
         * 与营养数值是两条独立的编辑链路——管理员今天改了热量，食品档案的 updated_at 不会动。
         * 契约 3.3 要求详情页展示「数据更新时间」指的是营养数值的，因此必须在营养表里单独给出。
         * 没有任何营养行时该表本身就会 40402，所以这里不会是"没有时间"的情况。
         */
        String dataUpdatedAt
) {

    /** 单个营养素的取值。 */
    public record Row(
            Long nutrientId,
            String nutrientCode,
            String nutrientName,
            String groupCode,
            Integer displayOrder,
            String valueStatus,
            String unit,
            BigDecimal amount,
            String displayText,
            Source source
    ) {
    }

    /** 数据来源。营养数值必须能追溯到具体来源（验收指标：可追溯率 100%）。 */
    public record Source(
            Long id,
            String name,
            String code,
            String sourceType,
            String applicableScope
    ) {
    }
}
