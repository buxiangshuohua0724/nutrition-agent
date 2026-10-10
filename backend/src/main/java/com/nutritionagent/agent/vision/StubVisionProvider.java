package com.nutritionagent.agent.vision;

import com.nutritionagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 未配置外部视觉服务时的降级实现。
 *
 * <p>它<b>不猜测</b>图片里是什么。可选的做法是"返回一批热门食品当候选"，但那等于
 * 把随机结果伪装成识别结果——用户会以为模型认出了他的图片。这里选择如实返回
 * {@code NEED_MANUAL_SEARCH}，让前端引导用户手动搜索（契约 3.6 对该状态的要求）。
 *
 * <p>整个项目在开发态默认走这条路径，因此"识别页在视觉服务缺失时是什么样"始终可见、
 * 可回归，而不是等到生产才发现。
 */
@Component
public class StubVisionProvider implements VisionProvider {

    private static final Logger log = LoggerFactory.getLogger(StubVisionProvider.class);

    private final AppProperties properties;

    public StubVisionProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean available() {
        return properties.getAi().getVision().isConfigured();
    }

    @Override
    public Outcome recognize(byte[] imageBytes, String mimeType) {
        if (properties.getAi().getVision().isConfigured()) {
            // 配置齐备但本实现不会真的调模型：宁可显式说明"尚未接入"，也不假装识别成功
            log.warn("视觉模型已配置但当前实现尚未接入外部调用，按不可用处理");
        }
        return new Outcome(
                "NEED_MANUAL_SEARCH",
                List.of(),
                "视觉服务未配置或尚未接入，无法从图片中识别食品",
                "vision-unavailable",
                0);
    }
}
