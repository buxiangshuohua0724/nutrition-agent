package com.nutritionagent.food.dto;

import java.math.BigDecimal;

/**
 * 列表项（首页卡片 / 搜索结果）—— 契约 3.1 {@code FoodSummary}。
 *
 * <p>{@code energyAvailable} 是三态语义在列表层的落点：营养数据缺失时它必须是
 * {@code false} 且 {@code energyKcal} 必须是 {@code null}，**绝不允许用 0 兜底**
 * （PRD 5.4；前端据此渲染「暂无数据」）。
 *
 * @param id               食品 id
 * @param code             业务编码
 * @param name             中文标准名
 * @param enName           英文名，可空
 * @param categoryId       分类 id，可空
 * @param categoryName     分类名，可空
 * @param categorySlug     分类 slug，可空（前端素材定位用）
 * @param coverImageUrl    封面图地址，无图时为 null（前端显示占位图，不是破图）
 * @param intro            简介，可空
 * @param defaultVariantId 默认形态 id，可空
 * @param energyKcal       每 100g 能量（kcal）；缺失或未知时为 null
 * @param energyAvailable  能量是否为已确认的数值
 * @param energyUnit       能量单位，跟随营养素字典（当前种子为 kcal）
 * @param energyText       能量的展示文本；缺失时为 null，由前端换用「暂无数据」
 * @param tags             展示用标签（当前为分类名）
 */
public record FoodSummary(
        Long id,
        String code,
        String name,
        String enName,
        Long categoryId,
        String categoryName,
        String categorySlug,
        String coverImageUrl,
        String intro,
        Long defaultVariantId,
        BigDecimal energyKcal,
        boolean energyAvailable,
        String energyUnit,
        String energyText,
        java.util.List<String> tags
) {
}
