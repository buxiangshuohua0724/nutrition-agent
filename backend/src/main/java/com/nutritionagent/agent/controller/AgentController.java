package com.nutritionagent.agent.controller;

import com.nutritionagent.agent.dto.AgentAnswer;
import com.nutritionagent.agent.dto.ChatResponse;
import com.nutritionagent.agent.llm.LanguageModel;
import com.nutritionagent.agent.service.AgentService;
import com.nutritionagent.agent.vision.VisionProvider;
import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.common.ratelimit.RateLimiter;
import com.nutritionagent.common.security.UserContext;
import com.nutritionagent.config.AppProperties;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Agent（契约 4.3）。
 *
 * <p>{@code /agent/capabilities} 是这套接口里最重要的一条：它让前端能知道"当前后端有没有
 * 接入真实模型、游客还剩多少额度"，从而在模型未配置时给出准确提示，而不是让用户以为
 * 系统坏了（PRD 6.5 的降级展示要求）。
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/agent")
public class AgentController {

    private final AgentService agentService;
    private final LanguageModel languageModel;
    private final VisionProvider visionProvider;
    private final AppProperties properties;

    public AgentController(AgentService agentService,
                           LanguageModel languageModel,
                           VisionProvider visionProvider,
                           AppProperties properties) {
        this.agentService = agentService;
        this.languageModel = languageModel;
        this.visionProvider = visionProvider;
        this.properties = properties;
    }

    public record ChatRequest(Long sessionId, @NotBlank String question, Long foodId, Long variantId, BigDecimal weight) {
    }

    public record SessionRequest(String title) {
    }

    /** 提交问题。返回体的 {@code degraded} 为真时，前端必须提示当前回答的完整度。 */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest body, HttpServletRequest request) {
        AgentService.ChatOutcome outcome = agentService.chat(
                body.sessionId(), body.question(), body.foodId(), body.variantId(), body.weight(),
                UserContext.current(), RateLimiter.clientIp(request), request.getHeader("User-Agent"));
        return Result.ok(new ChatResponse(outcome.sessionId(), outcome.sessionTitle(), outcome.answer()));
    }

    /** 会话列表（登录）。 */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        return Result.ok(agentService.listSessions(UserContext.current()));
    }

    /** 新建会话（游客也可）。 */
    @PostMapping("/sessions")
    public Result<Map<String, Object>> createSession(@RequestBody(required = false) SessionRequest body,
                                                     HttpServletRequest request) {
        String title = body == null ? null : body.title();
        return Result.ok(agentService.createSession(title, UserContext.current(), RateLimiter.clientIp(request)));
    }

    /** 会话消息。 */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(agentService.messages(id, UserContext.current(), RateLimiter.clientIp(request)));
    }

    /** 删除会话（软删，保留审计线索）。 */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        agentService.deleteSession(id, UserContext.current(), RateLimiter.clientIp(request));
        return Result.ok();
    }

    /**
     * 当前能力与限制。
     *
     * <p>{@code limitations} 里的每一条都对应一个真实的能力缺口，前端可以逐条展示；
     * 不写"AI 能力强大"这类无法验证的描述。
     */
    @GetMapping("/capabilities")
    public Result<Map<String, Object>> capabilities() {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean llmConfigured = properties.getAi().getLlm().isConfigured();
        boolean visionConfigured = visionProvider.available();
        data.put("modelConfigured", llmConfigured);
        data.put("visionConfigured", visionConfigured);
        data.put("guestChatQuota", properties.getSecurity().getGuestDailyChatLimit());
        data.put("guestRecognizeQuota", properties.getSecurity().getGuestDailyRecognitionLimit());
        data.put("answerModel", languageModel.name());

        List<String> limitations = new java.util.ArrayList<>();
        if (!llmConfigured) {
            limitations.add("未接入外部大模型：回答由确定性文本组织器生成，只复述数据库中的事实，不做推理与联想。");
        }
        if (!visionConfigured) {
            limitations.add("未配置视觉识别服务：上传图片后无法给出候选食品，请使用关键词搜索。");
        }
        limitations.add("营养数值以每 100g 可食部为参考值，按重量折算为线性估算，未考虑烹饪损失。");
        data.put("limitations", limitations);
        return Result.ok(data);
    }
}
