package com.nutritionagent.food.dto;

/** 食品详情中的图片项 —— 契约 3.2 {@code images[]}。 */
public record ImageView(
        Long id,
        String url,
        String thumbUrl,
        String imageType,
        String caption,
        String sourceNote,
        String licenseNote,
        boolean isCover,
        Integer sortOrder
) {
}
