package com.lab.securityoauth2authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * OAuth2 协议端点安全配置 — {@code @Order(1)} 最高优先级。
 *
 * <p>使用 Spring Authorization Server 的 {@code applyDefaultSecurity(http)}
 * 自动注册 OAuth2 协议端点（/oauth2/authorize、/oauth2/token 等）。</p>
 *
 * <p>两个过滤器链协作机制：
 * <pre>
 * 用户访问 /oauth2/authorize
 *   → @Order(1) 匹配 — 协议端点要求已认证
 *   → 用户未登录 → exceptionHandling 重定向到 /api/oauth2-auth/login
 *   → /login 不在 @Order(1) 范围内 → 跳过
 *   → @Order(2) 匹配 /login → formLogin 展示登录页 → 用户提交凭证
 *   → 认证成功 → 重定向回 /oauth2/authorize → @Order(1) 再次处理 → 已登录 → 展示授权页
 * </pre>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CONSENT_PAGE_URI = "/api/oauth2-auth/consent";

    /**
     * @Order(1) — 优先匹配 OAuth2 协议端点。
     * 未匹配的请求（如 /login）传递到 @Order(2) 的默认过滤器链。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // ① 注册 OAuth2 协议端点（authorize、token、jwks、revoke、introspect）
        // 该方法把授权端点/令牌端点等默认路径挂到过滤器链，并启用对应处理器
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        // ② 自定义授权确认页 + 启用 OIDC
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .authorizationEndpoint(authorizationEndpoint ->
                        authorizationEndpoint.consentPage(CONSENT_PAGE_URI))   // 授权确认页（自定义 URI，由 AuthorizationConsentController 渲染）
                .oidc(Customizer.withDefaults());                               // 启用 OpenID Connect（/userinfo 等 OIDC 端点）

        // ③ 浏览器访问 OAuth2 端点但未登录 → 重定向到登录页
        //    只对 HTML 请求（浏览器）生效，API 请求不受影响
        http.exceptionHandling(exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/api/oauth2-auth/login"),  // 未登录浏览器用户 → 302 到登录页
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));              // 仅当请求期望 HTML 响应时才走重定向

        return http.build();  // 组装出"协议端点优先"的第一条过滤器链
    }
}
