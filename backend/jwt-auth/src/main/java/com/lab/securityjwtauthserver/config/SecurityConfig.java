package com.lab.securityjwtauthserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT 无状态认证安全配置。
 *
 * <p>核心策略：STATELESS（无 Session）+ 禁用 CSRF + 自定义 JWT 过滤器。
 * JWT 自身携带用户信息，每次请求独立验证，服务器不保存任何会话状态。</p>
 *
 * <p>过滤器执行顺序（关键路径）：
 * <pre>
 *   请求 → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → FilterSecurityInterceptor → Controller
 *           ↑ 从 Authorization 头             ↑ 被禁用（formLogin disabled）
 *           提取 JWT 并恢复认证                  所以不会处理表单登录
 * </pre>
 *
 * <p>机制步骤 → 组件/配置映射（机制详解见 docs/auth-principles/JWT认证原理.md）：</p>
 * <pre>
 * 验证凭证            → AuthController 登录接口 → AuthenticationManager（下方 authenticationManager Bean）
 *                       → UserDetailsServiceImpl → PasswordEncoder.matches()
 * 组装 Claims + 签名   → JwtUtil.generateAccessToken()（jjwt，密钥 app.jwt.secret，有效期 app.jwt.access-token-expiration）
 * 拦截请求、提取 Token  → JwtAuthenticationFilter（下方 ④ addFilterBefore 挂入过滤器链）
 * 本地验签 + 检查过期    → JwtUtil.parseToken()（jjwt 自动验签 / 验期 / 验格式，异常即无效）
 * 恢复身份给业务代码     → JwtAuthenticationFilter 构建 3 参数 UsernamePasswordAuthenticationToken
 *                         → SecurityContextHolder.setAuthentication()
 * 拒绝未认证请求        → AuthorizationFilter（下方 ① authorizeHttpRequests 规则，SecurityContext 为空 → 401）
 * </pre>
 *
 * <p>三个关键策略（对应机制第 3 章的"无状态"）：
 * <ul>
 *   <li>STATELESS（③）— 不创建 HttpSession，认证状态只活在单个请求的 SecurityContext 中，请求结束即消失</li>
 *   <li>addFilterBefore（④）— 自定义过滤器插在表单登录过滤器之前：JWT 接管"谁已登录"，
 *       传统表单登录在本模块没有用武之地（登录走 AuthController 自定义接口）</li>
 *   <li>禁用 CSRF（②）— JWT 由 JS 手动放入 Authorization 头，浏览器不会自动携带，无 CSRF 攻击面</li>
 * </ul>
 */
@Configuration                // 声明为 Spring 配置类，启动时加载
@EnableWebSecurity            // 激活 Spring Security Web 安全（注册默认过滤器链）
@RequiredArgsConstructor      // Lombok：为所有 final 字段生成构造函数（构造注入）
public class SecurityConfig {

    // JWT 认证过滤器 — 不能声明为 @Bean，否则 Spring Boot 会自动注册到全局过滤器链
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // ================================================================
    // SecurityFilterChain — Spring Security 的核心：定义安全过滤器链
    // ================================================================

    /**
     * 配置安全过滤器链。
     *
     * <p>HttpSecurity 是流式 API，每一步配置一个安全维度。
     * 最终调用 {@code http.build()} 生成 SecurityFilterChain 注册到容器。</p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ---------- ① URL 访问权限 ----------
                // authorizeHttpRequests 是 Spring Security 6.x 的新 API（替代废弃的 antMatchers）
                .authorizeHttpRequests(authorize -> authorize
                        // 登录接口 + 公开接口：不需要认证（用户还没有 Token）
                        .requestMatchers(
                                "/api/jwt/auth/login",
                                "/api/jwt/public/**"
                        ).permitAll()
                        // 用户信息接口：需要认证（必须携带有效 JWT）
                        .requestMatchers("/api/jwt/profile").authenticated()
                        // ★ 默认拒绝策略：凡是上面没显式放行的，一律要求认证
                        .anyRequest().authenticated()
                )

                // ---------- ② CSRF 保护 ----------
                // JWT 存在 localStorage 中，浏览器不会自动发送，CSRF 攻击对它无效。
                // 如果启用 CSRF，每个 POST/PUT/DELETE 还需要额外带 _csrf token，对 REST API 无意义。
                .csrf(AbstractHttpConfigurer::disable)

                // ---------- ③ Session 管理 ----------
                // STATELESS = 永远不创建 HttpSession，每个请求独立认证。
                // SecurityContext 不会写入 Session，请求结束即清空（ThreadLocal）。
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ---------- ④ 插入自定义 JWT 过滤器 ----------
                // 放在 UsernamePasswordAuthenticationFilter 之前：
                // JWT 过滤器先检查 Authorization 头 → 有则完成认证 → 后面的表单过滤器跳过
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // ---------- ⑤ 禁用默认登录方式 ----------
                // REST API 不需要 HTML 登录页和 HTTP Basic 弹窗
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // ---------- ⑥ 异常处理 — 未认证/认证失败统一 JSON 401 ----------
                .exceptionHandling(ex -> ex
                        // 未登录访问受保护接口 / 登录凭证错误 → 返回 401 JSON（默认是 403，不符合 REST 语义）
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
                        })
                );

        return http.build();
    }

    // ================================================================
    // PasswordEncoder — 密码加密器（BCrypt）
    // ================================================================

    /**
     * BCrypt 密码编码器。
     *
     * <p>为什么 BCrypt？
     * <ul>
     *   <li>自带随机盐值 — 相同密码每次加密结果不同，彩虹表攻击无效</li>
     *   <li>计算成本可调 — 工作因子 10 = 2^10 次迭代</li>
     *   <li>不可逆 — 只能 matches() 比对，无法从密文还原明文</li>
     * </ul>
     *
     * <p>使用时机：
     * <ol>
     *   <li>登录 — DaoAuthenticationProvider 用它比对用户输入和 DB 密文</li>
     *   <li>初始化 — sql/init-*.sql 中的测试用户密码为 BCrypt 密文，不再由代码创建</li>
     * </ol>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 默认工作因子 10
    }

    // ================================================================
    // AuthenticationManager — 认证管理器
    // ================================================================

    /**
     * 暴露 AuthenticationManager 为 Bean。
     *
     * <p>AuthController 登录时需要手动调用 {@code authenticationManager.authenticate()}
     * 来验证用户名密码。默认 AuthenticationManager 可通过依赖注入获得，但显式声明为 Bean
     * 更清晰且便于测试。</p>
     *
     * <p>认证流程（内部）：
     * <pre>
     *   authenticate(token) → DaoAuthenticationProvider
     *     → UserDetailsService.loadUserByUsername()   // 从 DB 加载用户
     *     → BCryptPasswordEncoder.matches()           // 比对密码
     *     → 成功返回已认证 Authentication / 失败抛 BadCredentialsException
     * </pre>
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
