package com.lab.securityoauth2resourceserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 资源服务器安全配置。
 *
 * <p>核心职责：
 * <ol>
 *   <li>声明为 OAuth2 Resource Server（JWT 模式）— BearerTokenAuthenticationFilter 自动处理</li>
 *   <li>公开接口放行，受保护接口需要 SCOPE_read 权限</li>
 *   <li>无状态会话管理（STATELESS）</li>
 * </ol>
 *
 * <p>关键机制：
 * <pre>
 * 请求 → BearerTokenAuthenticationFilter（提取 Bearer Token）
 *      → JwtAuthenticationProvider（NimbusJwtDecoder 验证签名 — 公钥从 issuer-uri 自动获取）
 *      → JwtAuthenticationToken（已认证）→ FilterSecurityInterceptor（检查 scope）
 *      → Controller
 * </pre>
 *
 * <p>与 JWT 模块的关键区别：不需要自定义过滤器！
 * Spring Security 的 oauth2ResourceServer() 自动提供 BearerTokenAuthenticationFilter。
 * 只需要配置 issuer-uri，公钥自动发现和刷新。</p>
 *
 * <p>协议步骤映射（协议详解见 docs/auth-principles/OAuth2认证原理.md 第 2 章）：
 * ⑪ 提取 Bearer Token → BearerTokenAuthenticationFilter；
 * ⑫ 验签 + scope 检查 → JwtAuthenticationProvider（下方 hasAuthority("SCOPE_read")）。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)    // 启用 @PreAuthorize（Controller 方法级鉴权）
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/oauth2-resource/public/**").permitAll()  // 公开接口放行
                        .requestMatchers("/error").permitAll()  // 错误页放行，避免认证失败死循环
                        // ★ SCOPE_read → hasAuthority("SCOPE_read")
                        // Spring Security 将 JWT scope: "read" 自动映射为此格式
                        // 注：/api/oauth2-resource/userinfo 已删除 — Controller 无此端点，属死配置
                        .requestMatchers("/api/oauth2-resource/items").hasAuthority("SCOPE_read")  // 要求 JWT 携带 scope=read
                        .anyRequest().authenticated()  // 兜底：其余需认证
                )
                // ★ 启用 JWT Bearer Token 验证 — 公钥从 issuer-uri 自动获取
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()))  // 默认 JwtDecoder：按 iss/aud/exp/签名 全面校验
                // 资源服务器无状态 — 不需要 Session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 每个请求独立验签，不建会话
                .csrf(csrf -> csrf.disable());   // API 不使用 Cookie（无 CSRF 攻击面）

        return http.build();  // 过滤器链：BearerTokenAuthenticationFilter 等自动挂入
    }
}
