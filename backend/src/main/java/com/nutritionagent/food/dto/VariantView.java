package com.nutritionagent.food.dto;

import java.math.BigDecimal;

/** 食品详情中的形态项 —— 契约 3.2 {@code variants[]}。 */
public record VariantView(
        Long id,
        String state,
        String stateLabel,
        String cookingMethod,
        String nameSuffix,
        BigDecimal edibleRatio,
        String description,
        boolean isDefault
) {
}
