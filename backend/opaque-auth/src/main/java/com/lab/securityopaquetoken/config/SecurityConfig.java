package com.lab.securityopaquetoken.config;

import com.lab.securityopaquetoken.filter.TokenAuthenticationFilter;
import com.lab.securityopaquetoken.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Opaque Token 安全配置 — 与 JWT 同为 STATELESS，但 Token 验证方式完全不同。
 *
 * <p>核心区别：
 * <ul>
 *   <li>JWT — 本地验签（不查存储），Token 自带用户信息</li>
 *   <li>Opaque Token — 查 Redis（必须查存储），Token 只是 UUID</li>
 * </ul>
 *
 * <p>这意味着每个请求都会产生一次 Redis 查询，但 Redis 极快（通常 &lt;1ms），
 * 换来的是 Token 可随时撤销的能力。</p>
 *
 * <p>机制步骤 → 组件/配置映射（机制详解见 docs/auth-principles/OpaqueToken认证原理.md）：</p>
 * <pre>
 * 验证凭证 + 签发 Token → AuthController 登录接口 → TokenService.createToken()（UUID + 写 Redis + TTL）
 * Token 存储           → Redis，Key 前缀 opaque:token:（application.yml: app.token.redis-prefix / expire-seconds）
 * 拦截请求、提取 Token   → TokenAuthenticationFilter（下方 addFilterBefore 挂入过滤器链）
 * 查询验证             → TokenService.getTokenInfo()（GET Redis，Key 存在即有效）
 * 恢复身份             → TokenAuthenticationFilter 构建 3 参数 UsernamePasswordAuthenticationToken
 *                         → SecurityContextHolder.setAuthentication()
 * 登出撤销             → AuthController 登出接口 → TokenService.revokeToken()（DEL Redis，立即失效）
 * </pre>
 *
 * <p>与 JWT 模块（jwt-auth）完全同构：同样的 STATELESS + addFilterBefore 套路，
 * 唯一差异是"如何验证 Token"——JWT 本地验签（JwtUtil），Opaque 查 Redis（TokenService）。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // STATELESS — 无 Session，与 JWT 模块相同
                .csrf(csrf -> csrf.disable())  // 无 Cookie 会话即可安全关闭 CSRF（令牌只走 Authorization 头，浏览器不会自动携带）
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // 永不创建/使用 HttpSession，身份全靠请求头 Token
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/opaque/auth/login", "/api/opaque/public/**").permitAll()  // 登录与公开接口放行（登录前没有 Token 可带）
                        .anyRequest().authenticated()  // 兜底：其余接口必须携带有效 Token
                )
                // ★ TokenAuthenticationFilter 替代 JwtAuthenticationFilter
                // 挂在 UsernamePasswordAuthenticationFilter 之前，登录过滤器（已禁用）同位置的"自定义认证"占位
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())  // 禁用表单登录：认证入口只有 login 接口 + 过滤器链
                .httpBasic(basic -> basic.disable())  // 禁用 HTTP Basic：避免额外认证入口造成混淆
                // 未认证 → 返回 JSON 401
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);  // Token 缺失/无效时由 EntryPoint 统一输出 401 JSON
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"code\":401,\"message\":\"未登录或Token已过期\"}"
                            );
                        })
                )
                .userDetailsService(userDetailsService);  // 登录接口 AuthenticationManager 依赖的查库实现

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);  // 登录时 matches() 比对用，与注册入库时的 BCrypt 算法配套
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();  // 供 AuthController.login 手动触发认证
    }
}
