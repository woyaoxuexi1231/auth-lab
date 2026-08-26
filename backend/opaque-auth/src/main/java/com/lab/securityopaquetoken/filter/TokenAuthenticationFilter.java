package com.lab.securityopaquetoken.filter;

import com.lab.securityopaquetoken.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Opaque Token 认证过滤器 — 与 JwtAuthenticationFilter 的关键区别在于步骤②③。
 *
 * <pre>
 * ┌──────────────────┬───────────────────────────┬───────────────────────────┐
 * │ 步骤              │ JWT 过滤器                 │ Opaque Token 过滤器        │
 * ├──────────────────┼───────────────────────────┼───────────────────────────┤
 * │ ① 提取 Token     │ Authorization 头          │ Authorization 头           │
 * │ ② 解析 Token     │ JJWT 本地验签（不查存储）   │ 查 Redis（必须查存储）     │
 * │ ③ 获取用户信息    │ 从 JWT Payload 直接读取    │ 从 Redis 读取              │
 * │ ④ 认证恢复       │ SecurityContextHolder      │ SecurityContextHolder      │
 * └──────────────────┴───────────────────────────┴───────────────────────────┘
 * </pre>
 *
 * <p>步骤②③是核心区别。JWT 不查 Redis，Opaque Token 必须查 Redis。
 * 代价：每个请求一次 Redis 查询。收益：Token 可随时删除撤销。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;            // Token 服务：Redis 读写

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // ① 提取 Bearer Token（标准格式：Authorization: Bearer <token>）
        String authHeader = request.getHeader("Authorization");

        // 快速退出：没有 Token 或格式不对（后续由 EntryPoint 返回 401，不在此处拦截）
        // 大小写不敏感比较前 7 字符（RFC 7235：scheme 不分大小写，"bearer" 也应被接受）
        if (!StringUtils.hasText(authHeader)
                || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);  // 放行交给过滤器链后续环节（授权检查时自然失败）
            return;
        }

        String token = authHeader.substring(7);         // 去掉 "Bearer " 前缀，只保留 UUID 本体

        // ② 查 Redis 验证 Token（与 JWT 的关键区别！）
        // 判断 SecurityContext 是否已有认证：避免覆盖上游过滤器已完成的认证（STATELESS 下通常为 null）
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            TokenService.TokenInfo tokenInfo = tokenService.getTokenInfo(token);  // GET Redis：Key 存在=有效

            if (tokenInfo != null) {
                // ③ 从 Redis 读取的用户信息创建 Authentication
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                tokenInfo.username,                     // principal：认证主体（用户名）
                                null,                                   // credentials（不保留）：密码已核验过，不再持有
                                tokenInfo.toGrantedAuthorities()        // 从 Redis 读取的权限（角色集合）
                        );
                // 记录请求来源细节（IP、SessionID 等），便于审计与后续判断
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // ④ 放入 SecurityContextHolder → 认证恢复完成
                // 后续 AuthorizationFilter 与 @PreAuthorize 将从这里读取身份做授权判断
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } // Token 无效或过期 → 跳过认证，后续 FilterSecurityInterceptor 返回 401
        }

        // 无论认证与否都继续放行：授权决定交给链末的 AuthorizationFilter 统一执行（错误响应由 EntryPoint 输出）
        filterChain.doFilter(request, response);
    }
}
