package com.nutritionagent.food.dto;

/**
 * 分类列表项 —— 契约 4.1 {@code GET /categories}。
 *
 * <p>{@code foodCount} 必须是**统计出来的已发布食品数**：写死的数字一旦与库里不一致，
 * 就会出现「分类页说蔬菜 12 种、点进去是空列表」这种自相矛盾的界面。
 *
 * @param id       分类 id
 * @param name     分类名
 * @param slug     URL 标识（与前端素材文件名、CSS 变量名三方一致）
 * @param foodCount 该分类下已发布且已审核的食品数
 * @param iconUrl  图标地址；种子数据没有图标文件时为 null
 */
public record CategorySummary(
        Long id,
        String name,
        String slug,
        long foodCount,
        String iconUrl
) {
}
