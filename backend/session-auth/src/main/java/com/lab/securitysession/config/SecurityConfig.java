package com.lab.securitysession.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.securitysession.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.util.Map;

/**
 * Session 认证安全配置。
 *
 * <p>Session 认证是 Web 应用最经典的方式：
 * 用户登录 → 服务器创建 Session → 返回 JSESSIONID Cookie → 后续请求自动携带 Cookie。
 * Session 数据存入 Redis（替代默认内存存储），支持分布式和服务重启不丢失。</p>
 *
 * <p>与 JWT 模块的关键区别：
 * <ul>
 *   <li>不需要自定义过滤器 — Spring Security 内置的 SecurityContextHolderFilter 从 Session 恢复认证</li>
 *   <li>不需要 STATELESS — Session 本身就是"有状态"的</li>
 *   <li>CSRF 本应启用（本地学习中禁用以简化测试）</li>
 *   <li>前后端分离 SPA — 登录成功/失败返回 JSON，而非默认的 302 重定向</li>
 * </ul>
 *
 * <p>Session 认证流程：
 * <pre>
 *   【登录阶段】
 *   ① POST /api/session/login (username+password)
 *   ② UsernamePasswordAuthenticationFilter 拦截
 *   ③ AuthenticationManager.authenticate() → DaoAuthenticationProvider
 *   ④ 认证成功 → SecurityContext 存入 HttpSession
 *   ⑤ HttpSession 持久化到 Redis（spring-session-data-redis）
 *   ⑥ Response Header: Set-Cookie: JSESSIONID_LAB=abc123
 *
 *   【后续请求】
 *   ⑦ Request Header: Cookie: JSESSIONID_LAB=abc123
 *   ⑧ SecurityContextHolderFilter 从 Redis 恢复 SecurityContext
 *   ⑨ SecurityContextHolder.getContext().getAuthentication() → 当前用户
 * </pre>
 *
 * <p>机制步骤 → 组件映射（机制详解见 docs/auth-principles/Session认证原理.md）：</p>
 * <pre>
 * 拦截登录请求、解析凭证    → UsernamePasswordAuthenticationFilter（下方 formLogin 配置）
 * 验证凭证               → AuthenticationManager → DaoAuthenticationProvider
 * 查用户                 → UserDetailsService.loadUserByUsername()（UserDetailsServiceImpl）
 * 比对密码                → PasswordEncoder.matches()（BCryptPasswordEncoder）
 * 生成 Session ID、存储会话 → Servlet HttpSession + Spring Session 落 Redis
 * 下发 Cookie            → Set-Cookie: JSESSIONID_LAB=...（application.yml 配置 Cookie 名）
 * 恢复身份               → SecurityContextHolderFilter（内置，读 Session → SecurityContext）
 * 授权检查               → AuthorizationFilter（下方 authorizeHttpRequests 规则）
 * 登出销毁               → LogoutFilter + SecurityContextLogoutHandler（下方 logout 配置）
 * 并发会话控制            → SessionManagementFilter（下方 maximumSessions(1)）
 * </pre>
 *
 * <p>关键配置一览：
 * <ul>
 *   <li>Cookie 名 JSESSIONID_LAB — application.yml: server.servlet.session.cookie.name（默认 JSESSIONID）</li>
 *   <li>会话存 Redis，命名空间 spring:session:lab — application.yml: spring.session.redis.namespace（序列化选择见 RedisConfig）</li>
 *   <li>SPA 适配：登录/登出/未认证全部返回 JSON 而非默认的 302 重定向（下方 successHandler / failureHandler / authenticationEntryPoint）</li>
 *   <li>并发会话：同一账号最多 1 个有效会话，新登录踢旧（下方 maximumSessions(1) + maxSessionsPreventsLogin(false)）</li>
 *   <li>CSRF 已禁用 — 学习演示简化；生产环境必须启用</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity              // 激活 Spring Security Web 安全
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    /** Jackson 序列化器 — 统一 JSON 响应（替代易错字符串拼接） */
    private final ObjectMapper objectMapper;

    /** Session Cookie 名 — 唯一事实源在 application.yml（server.servlet.session.cookie.name），登出时按此名删除 */
    @Value("${server.servlet.session.cookie.name}")
    private String sessionCookieName;

    // ================================================================
    // SecurityFilterChain — Session 认证的核心配置
    // ================================================================

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ---------- ① CSRF 保护 ----------
                // Session 认证下浏览器自动携带 Cookie，CSRF 攻击可利用这一点。
                // 生产环境必须启用！此处仅为学习演示禁用。
                .csrf(csrf -> csrf.disable())   // 跳过 CSRF Token 校验（浏览器跨站请求防护；仅学习演示关闭）

                // ---------- ② URL 访问权限 ----------
                .authorizeHttpRequests(auth -> auth
                        // 公开接口 — 任何人可访问
                        .requestMatchers("/api/session/public/**").permitAll()  // 精确放行公开路径，不鉴权直接放行
                        // 管理员接口 — 仅 ROLE_ADMIN 可访问
                        .requestMatchers("/api/session/admin/**").hasRole("ADMIN")  // 要求 principal 拥有 ROLE_ADMIN 才放行（授权检查点）
                        // ★ 默认拒绝：未显式放行的一律需要认证
                        .anyRequest().authenticated()  // 兜底规则：其余所有请求都必须已登录，防止漏配导致越权
                )

                // ---------- ③ 表单登录 — SPA JSON 响应 ----------
                // 前端 Vue SPA 通过 fetch POST /api/session/login 发起登录
                // Spring Security 默认处理 /login，需要改为 /api/session/login
                // successHandler/failureHandler 返回 JSON（不重定向！）
                .formLogin(form -> form
                        .loginProcessingUrl("/api/session/login")    // 自定义登录处理 URL（UsernamePasswordAuthenticationFilter 拦截该地址）
                        // 登录成功 → 返回 JSON 200
                        .successHandler((request, response, authentication) -> writeJson(response, 200,
                                Map.of("code", 200, "message", "登录成功", "username", authentication.getName())))
                        // 登录失败 → 返回 JSON 401（不重定向到 /login?error）
                        .failureHandler((request, response, exception) -> writeJson(response, 401,
                                Map.of("code", 401, "message", "用户名或密码错误")))
                        .permitAll()    // 登录页面和登录请求不需要预先认证
                )

                // ---------- ④ 登出配置 ----------
                .logout(logout -> logout
                        // 仅 POST 可登出（CSRF 关闭时默认匹配所有方法，防止 <img> 等 GET 链接触发登出）
                        .logoutRequestMatcher(new AntPathRequestMatcher("/api/session/logout", "POST"))
                        .logoutSuccessHandler((request, response, authentication) -> writeJson(response, 200,
                                Map.of("code", 200, "message", "已登出")))  // 返回 JSON，前端跳转登录页
                        // 删除 Session Cookie（Cookie 名以 application.yml 为唯一事实源，避免双处硬编码）
                        .deleteCookies(sessionCookieName)
                )

                // ---------- ⑤ Session 管理 — 并发控制 ----------
                .sessionManagement(session -> session
                        // maximumSessions(1)：同一账号最多 1 个有效 Session
                        // maxSessionsPreventsLogin(false)：新登录踢掉旧 Session
                        .maximumSessions(1)  // 并发上限=1，防止同一账号多处登录（需 SessionRegistry 配合）
                        .maxSessionsPreventsLogin(false)  // false=允许新登录并把最旧的 Session 标记为过期踢掉
                )

                // ---------- ⑥ 异常处理 — JSON 401 而非重定向 ----------
                .exceptionHandling(ex -> ex
                        // 未登录访问受保护接口时由 EntryPoint 拦截（在过滤器链末尾触发）
                        .authenticationEntryPoint((request, response, authException) -> writeJson(response, 401,
                                Map.of("code", 401, "message", "未登录或登录已过期")))
                )

                // ---------- ⑦ 用户信息服务 ----------
                // 注入自定义 UserDetailsService：DaoAuthenticationProvider 登录时按用户名查库
                .userDetailsService(userDetailsService);

        return http.build();  // 组装过滤器链：以上配置生成安全过滤器链并注册到 Servlet 容器
    }

    // ================================================================
    // PasswordEncoder + AuthenticationManager
    // ================================================================

    /** BCrypt 密码编码器 — 强度 10（2^10 = 1024 轮迭代） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);  // 密码加密/比对均走 BCrypt，注册时加密、登录时 matches 比对
    }

    /** 暴露 AuthenticationManager — 供 AuthController 手动调用认证 */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();  // 由 Spring Boot 自动装配的认证管理器（内含 DaoAuthenticationProvider）
    }

    /** 监听 Session 销毁事件 → 同步 SessionRegistry（并发会话控制失效的常见缺漏） */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** 统一 JSON 响应：Map → Jackson 序列化（替代易错字符串拼接） */
    private void writeJson(HttpServletResponse response, int status, Map<String, Object> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
