package com.nutritionagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用自定义配置，对应 application.yml 的 {@code nutrition-agent.*}。
 *
 * <p>用类型化配置替代散落的 {@code @Value}，避免键名拼错只能运行时才发现。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nutrition-agent")
public class AppProperties {

    private Security security = new Security();
    private Storage storage = new Storage();
    private Ai ai = new Ai();

    @Data
    public static class Security {
        /** HMAC 签名密钥，生产必须通过 NA_TOKEN_SECRET 覆盖。 */
        private String tokenSecret;
        private int tokenTtlHours = 168;
        private int guestDailyChatLimit = 5;
        private int guestDailyRecognitionLimit = 3;
    }

    @Data
    public static class Storage {
        private String root = "./data/uploads";
        private String publicPrefix = "/api/v1/files";
    }

    @Data
    public static class Ai {
        private Model llm = new Model();
        private Model vision = new Model();

        @Data
        public static class Model {
            /**
             * 是否启用外部模型。为 false 时走确定性桩实现——
             * 这既是开发态默认值，也对应 PRD 6.5「外部模型不可用时非 AI 功能继续可用」的降级路径。
             */
            private boolean enabled = false;
            private String baseUrl;
            private String apiKey;
            private String model;
            private int timeoutSeconds = 30;

            /** 只要三项基本配置齐备才认为可用，避免半配置状态下发起必然失败的调用。 */
            public boolean isConfigured() {
                return enabled
                        && baseUrl != null && !baseUrl.isBlank()
                        && apiKey != null && !apiKey.isBlank()
                        && model != null && !model.isBlank();
            }
        }
    }
}
