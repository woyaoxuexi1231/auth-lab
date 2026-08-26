package com.lab.securityoauth2authserver.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * OAuth2 授权服务器核心配置。
 *
 * <p>四大职责：
 * <ol>
 *   <li>客户端注册 — {@link com.lab.securityoauth2authserver.service.DbRegisteredClientRepository}（数据库存储，非内存）</li>
 *   <li>JWT 签名密钥 — JWKSource（RSA 密钥对，授权服务器私钥签名）</li>
 *   <li>JWT 解码器 — JwtDecoder（用于内部验证）</li>
 *   <li>服务器设置 — AuthorizationServerSettings（issuer、端点路径）</li>
 * </ol>
 *
 * <p>客户端注册数据保存在 {@code oauth2_registered_client} 表（见 sql/init-oauth2.sql），
 * 不再硬编码在代码里；可通过授权服务器自带的客户端管理页面
 * {@code /api/oauth2-auth/clients} 增删（需 ROLE_ADMIN）。</p>
 *
 * <p>关键安全要点：
 * <ul>
 *   <li>client_secret 必须用 PasswordEncoder 编码存储 — 不能用 {noop} 明文</li>
 *   <li>redirect_uri 必须与客户端配置完全一致 — 否则报 invalid_redirect_uri</li>
 *   <li>issuer 必须与资源服务器的 issuer-uri 一致 — 否则验签失败</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class AuthorizationServerConfig {

    /** 授权服务器 issuer — 由 application-{profile}.yml 注入（必填，无默认值） */
    @Value("${spring.security.oauth2.authorizationserver.issuer}")
    private String issuer;

    /** RSA 私钥持久化文件路径（重启后已签发 JWT 仍有效；文件含私钥，切勿提交到仓库） */
    @Value("${app.jwk.persistence-file:}")
    private String jwkPersistenceFile;

    // ================================================================
    // ② Authorization 存储（内存）
    // ================================================================

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        // 授权记录（authorization code → token 的关联）存内存；生产应改 JDBC 实现防重启丢失
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService() {
        // 用户授权确认记录存内存；重启后用户需重新点"同意授权"
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    // ================================================================
    // ③ 授权服务器全局设置
    // ================================================================

    /**
     * 自定义 OAuth2 端点路径和 issuer。
     *
     * <p>issuer 会写入 JWT 的 iss 声明，资源服务器用它验证 token 来源。
     * 本地环境：http://localhost:18090（经网关），生产：公网网关地址。</p>
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)                                                 // JWT iss 声明（资源服务器验签依据）
                .authorizationEndpoint("/api/oauth2-auth/authorize")            // 授权端点（用户浏览器访问，换取授权码）
                .tokenEndpoint("/api/oauth2-auth/token")                        // 令牌端点（客户端用授权码换 Access Token）
                .jwkSetEndpoint("/api/oauth2-auth/jwks")                        // JWK 公钥端点（资源服务器拉取公钥验签）
                .tokenRevocationEndpoint("/api/oauth2-auth/revoke")             // 令牌吊销端点
                .oidcUserInfoEndpoint("/api/oauth2-auth/userinfo")              // OIDC 用户信息端点
                .build();
    }

    // ================================================================
    // ④ JWK 源 — RSA 密钥对（文件持久化，重启后已签发 JWT 仍有效）
    // ================================================================

    /**
     * 加载或生成 RSA 2048 密钥对 — 授权服务器用私钥签名 JWT，
     * 资源服务器通过 /jwks 端点获取公钥验证签名。
     *
     * <p>密钥优先从持久化文件加载（重启后 JWT 依然有效）；文件不存在则生成并落盘。
     * 持久化文件含私钥，属敏感文件，切勿提交到仓库（建议 .gitignore 忽略 jwk-key.json）。</p>
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = loadOrGenerateRsaKey();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        try {
            publicKey = rsaKey.toRSAPublicKey();      // 公钥 → 分发资源服务器（经 /jwks 端点）
            privateKey = rsaKey.toRSAPrivateKey();   // 私钥 → 签名 JWT（仅授权服务器持有）
        } catch (JOSEException e) {
            // 密钥结构解析异常（极端情况）：转换为运行时异常终止启动，避免带病运行
            throw new IllegalStateException("Failed to convert RSA key", e);
        }
        JWKSet jwkSet = new JWKSet(new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(rsaKey.getKeyID() != null ? rsaKey.getKeyID() : UUID.randomUUID().toString())  // 持久化后 kid 保持稳定
                .build());
        return new ImmutableJWKSet<>(jwkSet);                                   // 不可变 JWK 集（启动后公钥固定，直到重启）
    }

    /** 优先从持久化文件加载 RSA 密钥（重启后已签发 JWT 依然有效）；文件不存在则生成并落盘 */
    private RSAKey loadOrGenerateRsaKey() {
        if (StringUtils.hasText(jwkPersistenceFile)) {
            Path path = Path.of(jwkPersistenceFile);
            if (Files.exists(path)) {
                try {
                    String json = Files.readString(path);
                    JWK jwk = JWK.parse(json);
                    if (jwk instanceof RSAKey key && key.toRSAPrivateKey() != null) {
                        log.info("已从持久化文件加载 JWK: {}", jwkPersistenceFile);
                        return key;
                    }
                } catch (Exception e) {
                    log.warn("加载持久化 JWK 失败，将重新生成: {}", e.getMessage());
                }
            }
        }
        RSAKey rsaKey = generateRsaKey();
        if (StringUtils.hasText(jwkPersistenceFile)) {
            try {
                Files.createDirectories(Path.of(jwkPersistenceFile).toAbsolutePath().getParent());
                Files.writeString(Path.of(jwkPersistenceFile), rsaKey.toJSONString());
                log.info("已生成并持久化 JWK: {}", jwkPersistenceFile);
            } catch (Exception e) {
                log.warn("持久化 JWK 失败（仅本次运行有效）: {}", e.getMessage());
            }
        }
        return rsaKey;
    }

    /** 生成 RSA 2048 位密钥对并包装为 RSAKey（含私钥、随机 kid） */
    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }

    /** JWT 解码器 — 用 JWK 源验证 JWT 签名（授权服务器内部使用） */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);  // 按 iss/aud/签名规则解码并验证
    }

    /** DelegatingPasswordEncoder — 支持多种密码编码格式（{bcrypt}、{noop} 等） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 客户端密钥入库统一 {bcrypt} 前缀编码；绝不 {noop} 存明文
        return org.springframework.security.crypto.factory.PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }
}
