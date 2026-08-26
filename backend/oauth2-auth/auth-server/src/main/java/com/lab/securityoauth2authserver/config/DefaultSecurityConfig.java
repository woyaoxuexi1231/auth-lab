package com.lab.securityoauth2authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 默认安全配置 — {@code @Order(2)} 第二优先级。
 *
 * <p>职责：处理用户登录页面和业务端点认证。
 * OAuth2 协议端点已由 @Order(1) 的 {@link SecurityConfig} 处理。</p>
 *
 * <p>关键点：
 * <ul>
 *   <li>登录页 URL 和登录处理 URL 必须相同（/api/oauth2-auth/login）— GET 展示页面，POST 提交认证</li>
 *   <li>/error 必须放行 — 否则认证失败时会进入重定向死循环</li>
 *   <li>使用 Spring Security 默认的 formLogin 重定向机制（不需要 JSON 响应）— 因为是传统 Web 页面，非 SPA</li>
 * </ul>
 *
 * <p>协议步骤映射（协议详解见 docs/auth-principles/OAuth2认证原理.md 第 2 章）：
 * 授权服务器在协议中承担"③~⑦ 用户登录 + 同意授权"与"⑨~⑩ 换 Token / 签发"：
 * <ul>
 *   <li>③~⑦ 用户登录（本类 formLogin）+ 授权页（AuthorizationConsentController）</li>
 *   <li>⑨~⑩ 授权端点 /authorize、令牌端点 /token、/jwks、/userinfo — 由 @Order(1) 的
 *       {@link SecurityConfig} 与 {@link AuthorizationServerConfig}（四件套）提供</li>
 * </ul>
 * </p>
 *
 **/

@Configuration
public class DefaultSecurityConfig {

    @Bean
    @Order(2)   // 第二优先级 — 处理非 OAuth2 协议端点的所有请求
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // === URL 授权规则 ===
                .authorizeHttpRequests(authorize -> authorize
                        // 登录页面：所有人可访问（GET 展示 + POST 提交）
                        .requestMatchers("/api/oauth2-auth/login").permitAll()
                        // Chrome DevTools 探测路径
                        .requestMatchers("/.well-known/appspecific/**").permitAll()
                        // 登出弹窗（跨窗口通信）
                        .requestMatchers("/api/oauth2-auth/session/logout-popup").permitAll()
                        // 静态资源
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        // 公开 API
                        .requestMatchers("/api/oauth2-auth/public/**").permitAll()
                        // ★ /error 必须放行 — 认证失败时不放行会导致死循环
                        .requestMatchers("/error").permitAll()
                        // 客户端管理页：仅管理员可访问
                        .requestMatchers("/api/oauth2-auth/clients/**").hasRole("ADMIN")
                        // 默认拒绝：其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                // === 表单登录 ===
                // 背景：Spring Security 6 默认登录成功会 302 回“被拦截前的原始请求”并追加空的 ?continue 参数，
                // 它会混进 OAuth2 /authorize 参数（spring-authorization-server#1333 同类坑），地址栏还会出现“/continue”。
                // 完整前因后果见 AuthServerLoginSuccessHandler 类注释；这里用自定义 successHandler 处理。
                .formLogin(form -> form
                        .loginPage("/api/oauth2-auth/login")    // 自定义登录页（GET）（GET 展示、POST 认证都由 UsernamePasswordAuthenticationFilter 处理）
                        .successHandler(loginSuccessHandler())  // 登录成功：优先回原始请求（GET 去掉 ?continue）；无原始请求时进入客户端管理页
                        .permitAll())
                // === 登出 — 成功后重定向到登录页 ===
                .logout(logout -> logout
                        .logoutUrl("/api/oauth2-auth/logout")
                        .logoutSuccessUrl("/api/oauth2-auth/login?logout")  // 登出后回登录页并带提示参数
                        .permitAll()
                );

        return http.build();  // 组装"非协议端点"的常规过滤器链（含 formLogin/logout/授权规则）
    }

    /**
     * 登录成功处理器：回原始请求（去掉 ?continue 空参数）；无原始请求时落到客户端管理页。
     *
     * <p>为什么 defaultSuccessUrl 选 /api/oauth2-auth/clients 而不是 Spring Security 默认的 / ：
     * 用户直接打开登录页登录时 session 里没有 saved-request（没有被“拦截的原始请求”），
     * 此时登录成功会走 defaultSuccessUrl；默认的 / 在网关上没有路由会 404，
     * 落到客户端管理页才是 auth-server 独立使用的正确入口。</p>
     */
    @Bean
    public AuthServerLoginSuccessHandler loginSuccessHandler() {
        AuthServerLoginSuccessHandler handler = new AuthServerLoginSuccessHandler();
        handler.setDefaultTargetUrl("/api/oauth2-auth/clients");  // 无原始请求时的默认落地页
        return handler;
    }
}
