package com.nutritionagent.common.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 游客键派生。
 *
 * <p>「游客」没有账号，只能用某种来源标识来把多次请求归到同一个主体上——会话归属需要它，
 * 游客配额（PRD 5.2：游客有限次数 AI 问答与图片识别）也需要它。两处如果用不同的算法，
 * 就会出现「同一个人在这里算一次、在那里算另一次」的隐性不一致，因此集中在这里。</p>
 *
 * <p><b>不落 IP 明文</b>是为了减少不必要的个人信息留存；加盐（{@code token-secret}）使攻击者
 * 即使拿到这个摘要也无法用一张 IP 字典反查出对应地址。它<b>只用于归集同类请求，不作为鉴权依据</b>——
 * 拿到 guest_key 并不能访问别人的数据，因为每条记录仍要再比对归属。</p>
 */
@Component
public class GuestKeyResolver {

    /** 摘要取前 32 个十六进制字符（128 位）足够避免碰撞，且短于列宽 VARCHAR(64)。 */
    private static final int KEY_LENGTH = 32;

    private final String salt;

    public GuestKeyResolver(com.nutritionagent.config.AppProperties properties) {
        this.salt = String.valueOf(properties.getSecurity().getTokenSecret());
    }

    /**
     * 由客户端 IP 派生游客键。
     *
     * @param clientIp 客户端来源标识（已由 {@code RateLimiter.clientIp} 统一口径取出）
     * @return 32 字符的十六进制摘要；算法不可用时退化为固定前缀，功能不受影响
     */
    public String of(String clientIp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + "|" + clientIp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, KEY_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走到这里意味着运行环境异常。
            // 退化而不是抛出：限流与会话归属都不该让整个请求失败。
            return "guest-fallback";
        }
    }
}
