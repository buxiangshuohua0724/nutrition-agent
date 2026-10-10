package com.nutritionagent.agent.dto;

import java.util.List;

/**
 * 图片识别结果（契约 3.6）。
 *
 * <p>核心约束：{@code candidates} <b>不含任何营养数值字段</b>。视觉模型只负责回答
 * "这可能是哪种食品"，营养数据一律由结构化数据库提供（PRD 5.6）。
 */
public record ImageRecognitionResult(
        Long recognitionId,
        String status,
        String imageUrl,
        List<Candidate> candidates,
        Long confirmedFoodId,
        String notice,
        String failureReason,
        Integer latencyMs) {

    public record Candidate(
            Long foodId,
            String foodName,
            Double confidence,
            String reason,
            String coverImageUrl) {
    }
}
