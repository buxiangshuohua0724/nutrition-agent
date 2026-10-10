package com.nutritionagent.agent.dto;

import java.util.List;

/**
 * AI 问答的结构化回答（契约 3.5）。
 *
 * <p>三条硬规则（写在类型定义旁边，避免实现时遗忘）：
 * <ol>
 *   <li><b>字段按需返回</b>：不适用时为 {@code []} 或省略，不为填满字段而编造内容。</li>
 *   <li><b>{@code sources} 由后端工具层注入</b>：模型自报的来源一律丢弃（FR-011 验收）。</li>
 *   <li>{@code uncertainty} 非空时前端必须可见展示，不得静默丢弃。</li>
 * </ol>
 */
public record AgentAnswer(
        String answer,
        /** 问题类型（契约 3.5 / PRD 6.3 的九类问题），如 NUTRITION_QUERY、ENERGY_QUERY。 */
        String intent,
        List<FoodReference> foodReferences,
        List<NutritionFact> nutritionFacts,
        List<Recommendation> recommendations,
        List<Warning> warnings,
        List<Source> sources,
        List<Uncertainty> uncertainty,
        List<ToolCall> toolCalls,
        String model,
        Integer latencyMs,
        boolean degraded) {

    /** {@code coverImageUrl} 可为 null —— 种子库还没有授权图片，此时前端显示占位而不是破图。 */
    public record FoodReference(Long foodId, String foodName, Long variantId, String variantLabel,
                                String coverImageUrl) {
    }

    /**
     * 一条营养事实。
     *
     * <p>{@code amount} 是字符串而不是数字：它和 {@code displayText} 一样必须能表达
     * 「暂无数据」，用数值类型会导致缺失项被迫变成 0 或 null 后再被前端当 0 渲染。
     * {@code referenceNote} 与 {@code sourceName} 一并带上，回答里出现某个数值时
     * 用户要能就地看到「以什么为参考、出自哪里」。
     */
    public record NutritionFact(String foodName, String variantLabel, String nutrientCode, String nutrientName,
                                String amount, String unit, String valueStatus, String displayText,
                                String referenceNote, String sourceName) {
    }

    public record Recommendation(String text, String scope, String basis) {
    }

    public record Warning(String level, String text) {
    }

    /** {@code type} ∈ STRUCTURED_DATA / KNOWLEDGE / GUIDELINE。{@code refId} 必须能回到真实记录。 */
    public record Source(String type, String title, String refId, String url) {
    }

    public record Uncertainty(String code, String text) {
    }

    public record ToolCall(String tool, Object args, boolean success, Integer durationMs) {
    }
}
