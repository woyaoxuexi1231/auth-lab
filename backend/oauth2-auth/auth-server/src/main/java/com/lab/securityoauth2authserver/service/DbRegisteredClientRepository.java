package com.lab.securityoauth2authserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.securityoauth2authserver.entity.OAuth2Client;
import com.lab.securityoauth2authserver.mapper.OAuth2ClientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 基于 MySQL（MyBatis-Plus）的 {@link RegisteredClientRepository}。
 *
 * <p>替代原来的 {@code InMemoryRegisteredClientRepository}：客户端注册数据持久化到
 * {@code oauth2_registered_client} 表，启动/重启后依然存在，并且可以通过
 * 授权服务器自带的客户端管理页面（/api/oauth2-auth/clients）增删。</p>
 */
@Service
@RequiredArgsConstructor
public class DbRegisteredClientRepository implements RegisteredClientRepository {

    private static final int DEFAULT_ACCESS_TTL_SECONDS = 3600;    // 1 小时
    private static final int DEFAULT_REFRESH_TTL_SECONDS = 604800; // 7 天

    private final OAuth2ClientMapper clientMapper;

    @Override
    public void save(RegisteredClient registeredClient) {
        // 客户端注册/更新统一入口：转实体后按 id 是否存在决定插入还是更新
        OAuth2Client client = toEntity(registeredClient);
        if (clientMapper.selectById(client.getId()) != null) {
            clientMapper.updateById(client);  // 已存在 → 更新（编辑场景）
        } else {
            clientMapper.insert(client);      // 不存在 → 插入（注册场景）
        }
    }

    @Override
    public RegisteredClient findById(String id) {
        // 按 RegisteredClient.id（UUID）查询：授权服务内部管理 authorization 关联时使用
        return toRegisteredClient(clientMapper.selectById(id));
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        // 按业务 client_id 查询：令牌端点 /authorize 校验客户端身份时使用
        OAuth2Client client = clientMapper.selectOne(
                new LambdaQueryWrapper<OAuth2Client>().eq(OAuth2Client::getClientId, clientId));
        return toRegisteredClient(client);
    }

    // ==================== 实体 ⇄ RegisteredClient ====================

    /** DB 实体 → 授权服务器可用的 RegisteredClient（把逗号分隔字段拆回集合） */
    private RegisteredClient toRegisteredClient(OAuth2Client c) {
        if (c == null) {
            return null;  // 查不到客户端 → 返回 null，授权服务器会按"未知客户端"拒绝
        }
        RegisteredClient.Builder builder = RegisteredClient.withId(c.getId())
                .clientId(c.getClientId())
                .clientName(c.getClientName())
                .clientAuthenticationMethods(methods -> split(c.getClientAuthenticationMethods())
                        .forEach(m -> methods.add(new ClientAuthenticationMethod(m))))  // 认证方式：basic/post/none
                .authorizationGrantTypes(grants -> split(c.getAuthorizationGrantTypes())
                        .forEach(g -> grants.add(new AuthorizationGrantType(g))))        // 授权类型：authorization_code 等
                .redirectUris(uris -> split(c.getRedirectUris()).forEach(uris::add))     // 回调地址白名单
                .scopes(scopes -> split(c.getScopes()).forEach(scopes::add));            // 可申请 scope 白名单
        if (StringUtils.hasText(c.getClientSecret())) {
            builder.clientSecret(c.getClientSecret());  // 密文（{bcrypt} 前缀），留空则客户端无密钥
        }
        if (c.getClientIdIssuedAt() != null) {
            builder.clientIdIssuedAt(c.getClientIdIssuedAt().atZone(ZoneId.systemDefault()).toInstant());
        }
        if (StringUtils.hasText(c.getPostLogoutRedirectUris())) {
            builder.postLogoutRedirectUris(uris -> split(c.getPostLogoutRedirectUris()).forEach(uris::add));
        }
        // 客户端设置：PKCE 要求 + 是否强制用户授权确认
        builder.clientSettings(ClientSettings.builder()
                .requireProofKey(Boolean.TRUE.equals(c.getRequireProofKey()))            // true 时要求 code_challenge（防授权码拦截）
                .requireAuthorizationConsent(Boolean.TRUE.equals(c.getRequireAuthorizationConsent()))  // true 时展示授权确认页
                .build());
        // 令牌设置：Access/Refresh Token 的 TTL 与刷新令牌复用策略
        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(
                        ttlOrDefault(c.getAccessTokenTtlSeconds(), DEFAULT_ACCESS_TTL_SECONDS)))
                .refreshTokenTimeToLive(Duration.ofSeconds(
                        ttlOrDefault(c.getRefreshTokenTtlSeconds(), DEFAULT_REFRESH_TTL_SECONDS)))
                .reuseRefreshTokens(Boolean.TRUE.equals(c.getReuseRefreshTokens()))
                .build());
        return builder.build();
    }

    /** RegisteredClient → DB 实体（把集合拼回逗号分隔字符串） */
    private OAuth2Client toEntity(RegisteredClient rc) {
        OAuth2Client c = new OAuth2Client();
        c.setId(rc.getId());
        c.setClientId(rc.getClientId());
        c.setClientIdIssuedAt(rc.getClientIdIssuedAt() == null
                ? LocalDateTime.now()  // 授权服务器没填签发时间时取当前时刻
                : LocalDateTime.ofInstant(rc.getClientIdIssuedAt(), ZoneId.systemDefault()));
        c.setClientSecret(rc.getClientSecret());
        c.setClientName(rc.getClientName());
        c.setClientAuthenticationMethods(join(
                rc.getClientAuthenticationMethods().stream().map(ClientAuthenticationMethod::getValue)));
        c.setAuthorizationGrantTypes(join(
                rc.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue)));
        c.setRedirectUris(join(rc.getRedirectUris().stream()));
        c.setPostLogoutRedirectUris(join(rc.getPostLogoutRedirectUris().stream()));
        c.setScopes(join(rc.getScopes().stream()));
        c.setRequireProofKey(rc.getClientSettings().isRequireProofKey());
        c.setRequireAuthorizationConsent(rc.getClientSettings().isRequireAuthorizationConsent());
        Duration accessTtl = rc.getTokenSettings().getAccessTokenTimeToLive();
        Duration refreshTtl = rc.getTokenSettings().getRefreshTokenTimeToLive();
        c.setAccessTokenTtlSeconds(accessTtl == null ? DEFAULT_ACCESS_TTL_SECONDS : (int) accessTtl.toSeconds());
        c.setRefreshTokenTtlSeconds(refreshTtl == null ? DEFAULT_REFRESH_TTL_SECONDS : (int) refreshTtl.toSeconds());
        c.setReuseRefreshTokens(rc.getTokenSettings().isReuseRefreshTokens());
        return c;
    }

    /** 逗号分隔字符串 → 有序去重集合 */
    private static Set<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(Arrays.asList(value.split("\\s*,\\s*")));  // 按逗号（容忍空格）拆分
    }

    /** 集合 → 逗号分隔字符串（去空值） */
    private static String join(Stream<String> values) {
        return values.filter(StringUtils::hasText)
                .reduce((a, b) -> a + "," + b)  // 拼接为 "a,b,c"
                .orElse(null);                  // 空集 → null 存储
    }

    /** TTL 兜底：null 或非正数 → 使用默认值 */
    private static long ttlOrDefault(Integer value, int def) {
        return value == null || value <= 0 ? def : value;
    }
}
