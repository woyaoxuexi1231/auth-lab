package com.lab.securityjwtauthserver.config;

import com.lab.securityjwtauthserver.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 — 从 Authorization 头提取 JWT，验证并恢复认证状态。
 *
 * <p>职责：只负责"识别已登录用户"，不负责"拒绝未登录用户"。
 * 如果请求没有合法 JWT，过滤器静默跳过；后续 FilterSecurityInterceptor
 * 发现 SecurityContext 为空时会自动返回 401。</p>
 *
 * <p>继承 {@code OncePerRequestFilter}：保证每个请求只执行一次过滤逻辑，
 * 即使在 forward/include 场景下也不会重复执行。</p>
 *
 * <p>12 步执行流程（步骤④一次性解析 JWT，验签/过期/格式均在此完成，避免重复解析）：
 * <pre>
 * ① 取 Authorization 头 → ② 检查 Bearer 前缀 → ③ 提取 JWT 字符串
 * → ④ 一次性解析 JWT（验签+过期+格式）取 username → ⑤ 检查 SecurityContext 是否已有认证
 * → ⑥ 从 DB 加载 UserDetails → ⑦ 校验 JWT 用户名 == DB 用户名
 * → ⑧ 从已解析 Claims 取权限 → ⑨ 创建 UsernamePasswordAuthenticationToken
 * → ⑩ 设置 details → ⑪ 放入 SecurityContextHolder → ⑫ filterChain.doFilter() 继续
 * </pre>
 */
@Slf4j
@Component                      // 注册为 Spring Bean（但不是 @Bean 声明的 Filter）
@RequiredArgsConstructor        // 构造注入 JwtUtil + UserDetailsService
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;                      // JWT 工具：生成、解析、验证 JWT
    private final UserDetailsService userDetailsService; // 从数据库加载用户信息

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,       // @NonNull：参数为 null 时抛 NPE
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // === 步骤①：从 HTTP 请求头获取 Authorization ===
        // 格式示例：Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...
        String authHeader = request.getHeader("Authorization");

        // === 步骤②：快速退出 — 没有 Authorization 头或不是 Bearer 格式 ===
        // StringUtils.hasText() 检查 null、""、"  " 三种情况
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // 静默跳过，不阻断请求
            return;                                   // 后续 FilterSecurityInterceptor 会因为没有认证而返回 401
        }

        // === 步骤③：提取纯 JWT — 去掉 "Bearer " 前缀（正好 7 个字符）===
        String jwt = authHeader.substring(7);

        // === 步骤④：一次性解析 JWT（验签 + 过期 + 格式，全在 parseToken 内完成）===
        // 此前 extractUsername → isTokenValid → extractAuthorities 会重复解析 4 次，
        // 每次都要 Base64 解码 + HMAC 验签；改为解析一次后复用 Claims
        Claims claims;
        try {
            claims = jwtUtil.parseToken(jwt);
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            filterChain.doFilter(request, response); // 认证失败不阻断，让后续过滤器统一处理
            return;
        }
        String username = claims.getSubject();  // 从已解析的 Claims 直接取 sub（用户名）

        // === 步骤⑤：检查 SecurityContext 是否已有认证 ===
        // SecurityContextHolder 基于 ThreadLocal — 同一请求的任何地方都能获取
        // 如果已有 Authentication（被其他过滤器设置了），就不需要重复处理
        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // === 步骤⑥：从数据库重新加载用户（验证账号状态） ===
            // 虽然 JWT 已包含用户名，但仍需查 DB 确认：
            // ① 账号是否被禁用/锁定（enabled 字段）
            // ② 权限是否更新（DB 中的角色可能已变化）
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (Exception e) {
                log.warn("从数据库加载用户失败: {}", e.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            // === 步骤⑦：仅校验"JWT 用户名 == DB 用户名"（过期/签名已在步骤④校验）===
            if (!username.equals(userDetails.getUsername())) {
                log.warn("JWT 用户名与数据库用户不匹配");
                filterChain.doFilter(request, response);
                return;
            }

            // === 步骤⑧：从已解析的 Claims 取权限（零额外解析）===
            List<GrantedAuthority> authorities = jwtUtil.extractAuthorities(claims);

            // === 步骤⑨：创建已认证的 Authentication ===
            // ★ 关键：3 参数构造函数 → setAuthenticated(true)
            //   2 参数构造 = 未认证，3 参数构造 = 已认证
            //   credentials 设为 null — 安全起见不保留密码
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,  // principal：用户详情
                            null,         // credentials：null（不暴露密码）
                            authorities   // authorities：权限列表
                    );

            // === 步骤⑩：设置认证详情（IP、Session ID 等） ===
            // 用于审计日志、安全策略（如检测 IP 变化防 Token 盗用）
            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // === 步骤⑪：放入 SecurityContext ===
            // ★ 这是最关键的一步！设置后 Spring Security 认为当前请求已认证
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("JWT 认证恢复成功: 用户={}, 权限={}", username, authorities);
        }

        // === 步骤⑫：继续过滤器链 ===
        // 下一个过滤器可能是 FilterSecurityInterceptor（最终授权检查）
        // 请求结束后 Servlet 容器自动清除 ThreadLocal：
        //   SecurityContextHolder.clearContext()
        filterChain.doFilter(request, response);
    }
}
