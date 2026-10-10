package com.nutritionagent.agent.controller;

import com.nutritionagent.agent.dto.ImageRecognitionResult;
import com.nutritionagent.agent.service.RecognitionService;
import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.common.ratelimit.RateLimiter;
import com.nutritionagent.common.security.UserContext;
import com.nutritionagent.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 图片识别（契约 4.5）。
 *
 * <p>注意这里<b>没有</b>接收 userId 的位置：识别记录的归属由令牌（或游客 IP 派生键）
 * 决定，越权在这一层无法被表达（PRD 12.4）。
 */
@RestController
@RequestMapping(ApiPaths.V1)
public class RecognitionController {

    private final RecognitionService recognitionService;
    private final AppProperties properties;

    public RecognitionController(RecognitionService recognitionService, AppProperties properties) {
        this.recognitionService = recognitionService;
        this.properties = properties;
    }

    public record ConfirmRequest(@NotNull Long foodId) {
    }

    /** 上传图片识别（multipart 字段名固定为 file）。 */
    @PostMapping("/recognitions")
    public Result<ImageRecognitionResult> recognize(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        String ip = clientIp(request);
        return Result.ok(recognitionService.recognize(file, UserContext.current(), ip,
                request.getHeader("User-Agent"), properties.getSecurity().getGuestDailyRecognitionLimit()));
    }

    /** 确认候选食品。 */
    @PostMapping("/recognitions/{id}/confirm")
    public Result<ImageRecognitionResult> confirm(@PathVariable Long id,
                                                  @Valid @RequestBody ConfirmRequest body) {
        return Result.ok(recognitionService.confirm(id, body.foodId(), UserContext.current()));
    }

    /** 查询识别记录。 */
    @GetMapping("/recognitions/{id}")
    public Result<ImageRecognitionResult> get(@PathVariable Long id) {
        return Result.ok(recognitionService.get(id, UserContext.current()));
    }

    static String clientIp(HttpServletRequest request) {
        return RateLimiter.clientIp(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
    }
}