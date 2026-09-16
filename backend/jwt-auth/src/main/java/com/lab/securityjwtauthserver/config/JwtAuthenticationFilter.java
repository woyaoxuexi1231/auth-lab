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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 — 从 Authorization 头提取 JWT，验证并恢复认证状态。
 *
 * <p>职责：只负责"识别已登录用户"，不负责"拒绝未登录用户"。
 * 如果请求没有合法 JWT，过滤器静默跳过；后续 AuthorizationFilter
 * 发现 SecurityContext 为空时会自动返回 401。</p>
 *
 * <p>继承 {@code OncePerRequestFilter}：保证每个请求只执行一次过滤逻辑，
 * 即使在 forward/include 场景下也不会重复执行。</p>
 *
 * <p>执行流程（步骤④一次性解析 JWT，验签/过期/iss/aud 均在此完成，避免重复解析）：
 * <pre>
 * ① 取 Authorization 头 → ② 检查 Bearer 前缀 → ③ 提取 JWT 字符串
 * → ④ 解析 JWT（验签 + 验期 + 校验 iss/aud）→ ⑤ 取 sub → ⑥ 检查 SecurityContext 是否已有认证
 * → ⑦ 从 DB 加载 UserDetails → ⑧ 复查账号状态（enabled / locked / expired）
 * → ⑨ 权限取 DB 当前值 → ⑩ 创建 UsernamePasswordAuthenticationToken（3 参数 = 已认证）
 * → ⑪ 设置 details → ⑫ 放入 SecurityContextHolder → ⑬ filterChain.doFilter() 继续
 * </pre>
 *
 * <p>为什么步骤⑦⑧⑨要回查数据库？两条理由，且都只靠 JWT 自身做不到：
 * <ol>
 *   <li><b>账号状态</b>：DaoAuthenticationProvider 只在登录那一刻检查 enabled/locked，
 *       Token 有效期内不会再查。回查后管理员禁用账号<b>立即生效</b>，不必等 Token 过期。</li>
 *   <li><b>权限时效</b>：Token 里的 authorities 是签发时刻的快照，用它意味着角色调整
 *       要等 Token 过期才生效。此处统一以 DB 权限为准。</li>
 * </ol>
 * 代价是每请求一次用户查询（严格来说仍是"无状态"——服务端不存会话，
 * 只是读了权威数据源）。若追求纯本地验签，把 ⑦⑧⑨ 去掉、改用 Claims 里的权限即可，
 * 那正是 JWT "自包含、零回查"的形态。</p>
 */
@Slf4j
@Component                      // 注册为 Spring Bean（由 SecurityConfig 注入；同时用 FilterRegistrationBean 禁止容器重复注册）
@RequiredArgsConstructor        // 构造注入 JwtUtil + UserDetailsService
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;                       // JWT 工具：生成、解析、验证 JWT
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
            return;                                   // 后续 AuthorizationFilter 会因为没有认证而返回 401
        }

        // === 步骤③：提取纯 JWT — 去掉 "Bearer " 前缀（正好 7 个字符）===
        String jwt = authHeader.substring(7);

        // === 步骤④：一次性解析 JWT（验签 + 验期 + iss/aud 校验，全在 parseToken 内完成）===
        // 取用户名 / 判过期 / 取权限各解析一次的话，一个请求要重复验签 3~4 遍，
        // 每次都是 Base64 解码 + HMAC 计算；因此这里解析一次，后续全部复用 Claims
        Claims claims;
        try {
            claims = jwtUtil.parseToken(jwt);
        } catch (Exception e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            filterChain.doFilter(request, response); // 认证失败不阻断，让后续过滤器统一处理
            return;
        }
        String username = claims.getSubject();  // === 步骤⑤：从已解析的 Claims 取 sub（用户名）===

        // sub 为空说明 Token 结构不合法（本服务签发的 Token 必有 sub），直接跳过，避免拿 null 查库
        if (!StringUtils.hasText(username)) {
            log.warn("JWT 缺少 sub 声明，忽略该 Token");
            filterChain.doFilter(request, response);
            return;
        }

        // === 步骤⑥：检查 SecurityContext 是否已有认证 ===
        // SecurityContextHolder 基于 ThreadLocal — 同一请求的任何地方都能获取
        // 如果已有 Authentication（被其他过滤器设置了），就不需要重复处理
        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // === 步骤⑦：从数据库加载用户（权威数据源）===
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (Exception e) {
                log.warn("从数据库加载用户失败: {}", e.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            // === 步骤⑧：复查账号状态 ===
            // 这四个标志只在登录时被 DaoAuthenticationProvider 检查过，
            // Token 有效期内不会复查 —— 不在这里补检，被禁用的账号仍能继续访问
            if (!userDetails.isEnabled()
                    || !userDetails.isAccountNonLocked()
                    || !userDetails.isAccountNonExpired()
                    || !userDetails.isCredentialsNonExpired()) {
                log.warn("账号状态异常（已禁用/锁定/过期），不恢复认证: {}", username);
                filterChain.doFilter(request, response);
                return;
            }

            // === 步骤⑨：权限以数据库为准 ===
            // 不用 Claims 里的 authorities：那是签发时刻的快照，会让角色调整延迟到 Token 过期才生效
            List<GrantedAuthority> authorities = List.copyOf(userDetails.getAuthorities());
            logIfTokenAuthoritiesStale(claims, authorities, username);

            // === 步骤⑩：创建已认证的 Authentication ===
            // ★ 关键：3 参数构造函数 → setAuthenticated(true)
            //   2 参数构造 = 未认证，3 参数构造 = 已认证
            //   credentials 设为 null — 安全起见不保留密码
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,  // principal：用户详情（AuthController 直接强转使用）
                            null,         // credentials：null（不暴露密码）
                            authorities   // authorities：权限列表（来自 DB）
                    );

            // === 步骤⑪：设置认证详情（IP、Session ID 等） ===
            // 用于审计日志、安全策略（如检测 IP 变化防 Token 盗用）
            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // === 步骤⑫：放入 SecurityContext ===
            // ★ 这是最关键的一步！设置后 Spring Security 认为当前请求已认证
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("JWT 认证恢复成功: 用户={}, 权限={}", username, authorities);
        }

        // === 步骤⑬：继续过滤器链 ===
        // 下一个过滤器是 AuthorizationFilter（最终授权检查）
        // 请求结束后 Servlet 容器自动清除 ThreadLocal：
        //   SecurityContextHolder.clearContext()
        filterChain.doFilter(request, response);
    }

    /**
     * Token 内嵌权限与 DB 当前权限不一致时记一条 DEBUG 日志。
     *
     * <p>用于观察"JWT 权限快照会过期"这一现象。授权结果不受影响 ——
     * 本过滤器的权限始终取自 DB（步骤⑨）。</p>
     */
    private void logIfTokenAuthoritiesStale(Claims claims,
                                            List<GrantedAuthority> dbAuthorities,
                                            String username) {
        if (!log.isDebugEnabled()) {
            return;                                  // 非 DEBUG 级别不构造集合，零开销
        }
        Set<String> inToken = jwtUtil.extractAuthorities(claims).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        Set<String> inDb = dbAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (!inToken.equals(inDb)) {
            log.debug("Token 权限快照已过期（仍以 DB 为准）: 用户={}, Token={}, DB={}", username, inToken, inDb);
        }
    }
}
