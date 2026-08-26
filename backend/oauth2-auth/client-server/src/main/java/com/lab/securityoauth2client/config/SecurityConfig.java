package com.lab.securityoauth2client.config;

import com.lab.securityoauth2client.exception.OAuth2BindingRequiredException;
import com.lab.securityoauth2client.service.CustomOAuth2UserService;
import com.lab.securityoauth2client.service.CustomOidcUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * OAuth2 客户端安全配置 — 同时支持本地表单登录 + OAuth2 三方登录 + 账号绑定。
 *
 * <p>认证方式：
 * <ol>
 *   <li>本地表单登录 — POST /api/oauth2-client/auth/login（URL 编码表单）</li>
 *   <li>OAuth2 三方登录 — GET /api/oauth2-client/authorization/{provider} 发起授权</li>
 *   <li>已绑定 OAuth → 自动登录；未绑定 → 重定向到绑定页</li>
 * </ol>
 *
 * <p>OAuth2 登录流程（关键）：
 * <pre>
 * ① 用户点击"Lab 登录" → GET /api/oauth2-client/authorization/lab-client
 * ② OAuth2AuthorizationRequestRedirectFilter 构建授权 URL → 302 到认证服务器
 * ③ 用户在认证服务器登录 + 授权 → 302 回调 /api/oauth2-client/login/oauth2/code/lab-client?code=xxx
 * ④ OAuth2LoginAuthenticationFilter 拦截 → 用 code 换 Token → 调 /userinfo 获取用户信息
 * ⑤ CustomOAuth2UserService / CustomOidcUserService 检查绑定状态
 *    → 已绑定：注入 local_user 信息，正常登录
 *    → 未绑定：暂存 session → 抛 OAuth2BindingRequiredException
 * ⑥ failureHandler 检测 OAuth2BindingRequiredException → 重定向到 /#/bind
 * </pre>
 *
 * <p>协议步骤 → 组件映射（协议详解见 docs/auth-principles/OAuth2认证原理.md 第 2 章）：</p>
 * <pre>
 * ① 发起授权、重定向授权服务器 → OAuth2AuthorizationRequestRedirectFilter（下方 authorizationEndpoint.baseUri）
 * ⑧ 回调客户端                → OAuth2LoginAuthenticationFilter（下方 loginProcessingUrl 拦截 /login/oauth2/code/*）
 * ⑨~⑩ 后端通道换 Token        → 同上过滤器内部完成（OAuth2AuthorizationCodeGrantRequest，浏览器全程不可见）
 * 取用户信息                  → userInfoEndpoint：CustomOAuth2UserService（标准 OAuth2，GitHub）
 *                              / CustomOidcUserService（OIDC，lab-client、Google）
 * ⑤ 用户信息映射 + 账号绑定     → 上述两个 UserService + user_oauth_binding 表
 * </pre>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** 前端基地址 — 由 application-{profile}.yml 注入（必填，无默认值） */
    @Value("${app.frontend-base-url}")
    private String frontendBase;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // === URL 授权 ===
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/error", "/favicon.ico").permitAll()  // 首页与静态资源放行
                        .requestMatchers("/api/oauth2-client/public/**",
                                "/api/oauth2-client/auth/**",          // 注册/登录/状态查询（登录前可访问）
                                "/api/oauth2-client/oauth-pending",    // 读取待绑定信息（登录前可访问）
                                "/api/oauth2-client/bind/**",          // 绑定流程（登录前可访问）
                                "/api/oauth2-client/authorization/**", // OAuth2 授权发起入口（未登录时用户点击触发）
                                "/api/oauth2-client/login/**").permitAll()  // OAuth2 回调端点（必须放行，否则无法完成登录）
                        .anyRequest().authenticated()  // 兜底：其余接口需登录
                )

                // === 本地表单登录 — JSON 响应 ===
                .formLogin(form -> form
                        .loginProcessingUrl("/api/oauth2-client/auth/login")  // 本地登录提交地址（SPA 用 fetch 提交）
                        .successHandler((request, response, authentication) ->
                                writeJson(response, 200,  // 成功 → 返回 JSON，前端跳首页
                                        "{\"success\":true,\"username\":\""
                                                + authentication.getName() + "\"}"))
                        .failureHandler((request, response, exception) ->
                                writeJson(response, 401, "{\"error\":\"用户名或密码错误\"}"))
                        .permitAll()
                )

                // === OAuth2 三方登录 ===
                .oauth2Login(oauth2 -> oauth2
                        // ★ 回调 URL 使用通配符 /* 避免 Spring Security 6.3 路径匹配 bug
                        .loginProcessingUrl("/api/oauth2-client/login/oauth2/code/*")
                        // 授权请求入口
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/api/oauth2-client/authorization"))  // GET /authorization/{provider} → 302 到认证服务器
                        // 未登录时跳转前端登录页
                        .loginPage(frontendBase + "/#/login")
                        // 登录成功后跳转
                        .defaultSuccessUrl(frontendBase + "/#/oauth2", true)
                        // 用户信息服务
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)     // 标准 OAuth2（GitHub 等，查 /userinfo）
                                .oidcUserService(customOidcUserService))  // OIDC（lab-client/Google，读 id_token）
                        // 登录失败处理
                        .failureHandler((request, response, exception) -> {
                            String errorCode = "unknown";
                            if (exception instanceof OAuth2AuthenticationException oauth2Ex) {
                                errorCode = oauth2Ex.getError().getErrorCode();  // 提取协议错误码
                            }

                            // ★ 未绑定 → 跳转到绑定页
                            if (exception instanceof OAuth2BindingRequiredException bindEx) {
                                response.sendRedirect(frontendBase
                                        + "/#/bind?provider=" + bindEx.getProvider());  // 前端打开绑定页
                                return;
                            }

                            // authorization_request_not_found 自动重试一次
                            // （浏览器拦截/重定向丢 session 导致授权请求记录丢失时，重发起一次）
                            boolean retry = "1".equals(request.getParameter("retry"));
                            if (!retry && "authorization_request_not_found".equals(errorCode)) {
                                response.sendRedirect(
                                        "/api/oauth2-client/authorization/lab-client?retry=1");
                                return;
                            }

                            response.sendRedirect(frontendBase + "/#/oauth2?error=" + errorCode);  // 其余错误 → 前端错误提示
                        })
                )

                // === 登出 ===
                .logout(logout -> logout
                        // 仅 POST 可登出（CSRF 关闭时默认匹配所有方法，防止 <img> 等 GET 链接触发登出）
                        .logoutRequestMatcher(new AntPathRequestMatcher("/api/oauth2-client/logout", "POST"))
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":200,\"message\":\"已登出\"}");  // JSON 响应适配 SPA
                        })
                        .permitAll()
                )

                // === CSRF — API 端点禁用，OAuth2 回调也不需要 ===
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/oauth2-client/**")  // 本项目全部走 JSON API/回调，无浏览器表单 CSRF 面
                        .disable()
                )

                .userDetailsService(userDetailsService);  // 本地 formLogin 的查库实现

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();  // 供 AuthController/BindController 手动认证
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();  // {bcrypt} 前缀格式（与注册编码一致）
    }

    /** 带超时控制的 RestTemplate — 防止资源服务器无响应时请求无限挂起 */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);   // 连接超时 3s
        factory.setReadTimeout(5000);      // 读取超时 5s
        return new RestTemplate(factory);
    }

    /** 写入 JSON 响应（简化 successHandler/failureHandler 代码） */
    private void writeJson(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json);
    }
}
