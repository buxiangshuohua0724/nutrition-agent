package com.nutritionagent.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按重量折算的结果 —— 契约 3.4 {@code NutritionCalculation}。
 *
 * <p>折算公式是确定性的、可独立复算的（PRD 5.4）：
 * {@code 摄入量 = 每 referenceAmount 含量 × 实际重量 ÷ referenceAmount}。
 * 这里不含任何模型推断，因此结果必须可被单元测试逐位复算。
 */
public record NutritionCalculation(
        Long foodId,
        String foodName,
        Long variantId,
        String variantLabel,
        BigDecimal inputWeight,
        String inputUnit,
        BigDecimal referenceAmount,
        String referenceUnit,
        List<Item> items,
        boolean estimated,
        String estimatedReason,
        List<String> missingNutrients,
        List<NutritionTable.Source> sources
) {

    /**
     * 单个营养素的折算明细。
     *
     * <p>{@code originalAmount} 是每参考重量的原值，{@code computedAmount} 是按实际重量折算后的值；
     * 两者都带出来，是为了让界面能显示完整算式、让用户自己复核。
     */
    public record Item(
            Long nutrientId,
            String nutrientCode,
            String nutrientName,
            String unit,
            BigDecimal originalAmount,
            BigDecimal computedAmount,
            String displayText,
            String formula
    ) {
    }
}
