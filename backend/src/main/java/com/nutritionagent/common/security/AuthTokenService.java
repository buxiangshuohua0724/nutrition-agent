package com.nutritionagent.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nutritionagent.config.AppProperties;
import com.nutritionagent.user.entity.SysAuthToken;
import com.nutritionagent.user.entity.SysUser;
import com.nutritionagent.user.mapper.SysAuthTokenMapper;
import com.nutritionagent.user.mapper.SysUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 有状态登录令牌（ADR-10，用户已确认的方案）。
 *
 * <p>与 JWT 的取舍：令牌是随机 256 位串，数据库只存 SHA-256 散列。
 * <ul>
 *   <li>登出、改密、管理员封禁都能**真正吊销**（删行或置 {@code revoked_at}），
 *       JWT 在过期前无法作废；</li>
 *   <li>不引入 HMAC 密钥分发问题：{@code nutrition-agent.security.token-secret}
 *       只用于（可选的）令牌前缀域隔离，泄露它无法伪造令牌；</li>
 *   <li>代价是每次请求多一次索引查询（{@code uk_token_hash} 唯一索引），
 *       对本项目的规模可以接受。</li>
 * </ul>
 */
@Service
public class AuthTokenService {

    /** 令牌原文长度：32 字节熵，Base64 URL 无填充编码后 43 个字符。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysAuthTokenMapper tokenMapper;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final long ttlHours;

    public AuthTokenService(SysAuthTokenMapper tokenMapper, SysUserMapper userMapper,
                            JdbcTemplate jdbc, AppProperties properties) {
        this.tokenMapper = tokenMapper;
        this.userMapper = userMapper;
        this.jdbc = jdbc;
        this.ttlHours = properties.getSecurity().getTokenTtlHours();
    }

    /** 签发新令牌，返回**原文**（仅此一次可见，之后任何地方都拿不到）。 */
    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now();
        SysAuthToken token = new SysAuthToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusHours(ttlHours));
        tokenMapper.insert(token);
        return raw;
    }

    /**
     * 解析令牌原文为调用者身份。
     *
     * @return 令牌有效且用户可登录时返回身份；否则返回 {@code null}
     *         （**不抛异常**：是否要求登录由各接口决定）
     */
    public UserPrincipal resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        SysAuthToken token = tokenMapper.selectOne(
                Wrappers.<SysAuthToken>lambdaQuery().eq(SysAuthToken::getTokenHash, hash(rawToken)));
        if (token == null || token.getRevokedAt() != null) {
            return null;
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }

        SysUser user = userMapper.selectById(token.getUserId());
        // 用户已注销（逻辑删除）或非 ACTIVE 时，令牌立即失效 —— 这是有状态令牌的价值所在
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return null;
        }

        Set<UserRole> roles = rolesOf(user.getId());
        return new UserPrincipal(token.getId(), user.getId(), user.getUsername(),
                user.getNickname(), roles);
    }

    /** 吊销指定令牌。返回是否真的改动了一行。 */
    public boolean revoke(Long tokenId) {
        if (tokenId == null) {
            return false;
        }
        SysAuthToken token = tokenMapper.selectById(tokenId);
        if (token == null || token.getRevokedAt() != null) {
            return false;
        }
        token.setRevokedAt(LocalDateTime.now());
        return tokenMapper.updateById(token) > 0;
    }

    /** 吊销某用户的全部有效令牌（改密、封禁、注销时使用）。 */
    public int revokeAllOfUser(Long userId) {
        SysAuthToken patch = new SysAuthToken();
        patch.setRevokedAt(LocalDateTime.now());
        return tokenMapper.update(patch, Wrappers.<SysAuthToken>lambdaUpdate()
                .eq(SysAuthToken::getUserId, userId)
                .isNull(SysAuthToken::getRevokedAt));
    }

    /**
     * 角色查询。{@code sys_user_role} 是复合主键关联表、{@code sys_role} 是字典表，
     * 都没有逻辑删除列，故用一条显式 JOIN 而不是两次 ORM 查询。
     */
    public Set<UserRole> rolesOf(Long userId) {
        List<String> codes = jdbc.queryForList(
                "SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ?", String.class, userId);

        Set<UserRole> roles = new HashSet<>();
        for (String code : codes) {
            UserRole.of(code).ifPresent(roles::add);
        }
        // 任何已登录用户在权限模型上都隐含 USER 档位（否则"登录了却连收藏都不行"）
        roles.add(UserRole.USER);
        return roles;
    }

    /** SHA-256 十六进制散列。表中只存这个值，原文不可反推。 */
    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // JDK 必须提供 SHA-256，走到这里说明运行环境损坏，属于不可恢复错误
            throw new IllegalStateException("JVM 不支持 SHA-256，无法进行令牌散列", ex);
        }
    }
}
