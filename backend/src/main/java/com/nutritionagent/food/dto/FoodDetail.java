package com.nutritionagent.food.dto;

import java.util.List;

/**
 * 食品详情 —— 契约 3.2 {@code FoodDetail}。
 *
 * <p>只包含「食品是什么」的事实字段，**不含任何营养数值**：营养永远从
 * {@code GET /foods/{id}/nutrition} 按形态单独获取（[D1] 营养数据挂在形态上）。
 */
public record FoodDetail(
        Long id,
        String code,
        String name,
        String enName,
        String scientificName,
        CategoryRef category,
        List<AliasView> aliases,
        String intro,
        String commonUsage,
        String origin,
        String storageMethod,
        String suitableFor,
        String cautions,
        List<ImageView> images,
        List<VariantView> variants,
        String dataUpdatedAt,
        String publishStatus
) {

    /** 详情里的分类引用。 */
    public record CategoryRef(Long id, String name, String slug) {
    }

    /** 详情里的别名项。 */
    public record AliasView(String alias, String aliasType) {
    }
}
