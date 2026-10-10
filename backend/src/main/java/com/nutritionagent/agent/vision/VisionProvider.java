package com.nutritionagent.agent.vision;

import java.util.List;

/**
 * 视觉识别能力的抽象。
 *
 * <p>这一层的存在是为了让「视觉服务不可用」变成一种<b>正常的、可测试的</b>返回，
 * 而不是一个异常分支：PRD 6.5 要求外部模型不可用时，搜索/详情/营养查询等非 AI 功能
 * 必须继续可用；识别功能本身则应给出明确的"请手动搜索"引导，而不是 500。
 */
public interface VisionProvider {

    /** 单次识别的产物。{@code candidates} 只含食品标识与理由，<b>不含营养数值</b>。 */
    record Outcome(String status, List<Candidate> candidates, String failureReason, String modelName, int latencyMs) {
    }

    record Candidate(Long foodId, String foodName, Double confidence, String reason, String coverImageUrl) {
    }

    /** 能力是否可用（供 {@code /agent/capabilities} 与 /或识别接口提示前端）。 */
    boolean available();

    /**
     * 识别一张图片。
     *
     * <p>实现必须保证：不可用时返回 {@code status = NEED_MANUAL_SEARCH} 且候选为空，
     * <b>绝不返回空列表却声称 SUCCESS</b>。
     */
    Outcome recognize(byte[] imageBytes, String mimeType);
}
