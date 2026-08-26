package com.lab.securityoauth2authserver.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * auth-server 独立登录成功处理器。
 *
 * <h3>前因：登录后地址栏为什么会出现 “/continue”</h3>
 * <p><b>这不是本项目自己的页面/路由，也不是 Spring Authorization Server 生成的路径</b>，
 * 而是 Spring Security 6 的 {@code HttpSessionRequestCache} 续跳标记：</p>
 * <ol>
 *   <li>你访问了一个需要登录的地址 A（如 /api/oauth2-auth/clients 或
 *       /api/oauth2-auth/authorize?...），Spring Security 把 A 存进 session，302 到登录页；</li>
 *   <li>登录成功后，默认的 SavedRequestAwareAuthenticationSuccessHandler 会 302 回 A，
 *       并在 URL 上追加一个<b>空的 {@code ?continue}</b> 参数（标记“这次请求要恢复 session
 *       里存的原始请求”）；</li>
 *   <li>RequestCacheAwareFilter 看到 {@code ?continue} 后把当前请求“回放”成原始请求 A。</li>
 * </ol>
 *
 * <h3>为什么不能直接用默认行为</h3>
 * <ol>
 *   <li>这个空的 {@code continue} 参数会混进 OAuth2 {@code /authorize} 的请求参数，被
 *       Spring Authorization Server 1.3 持久化到授权上下文里，导致令牌交换等环节的兼容问题
 *       （spring-authorization-server #1333 同类问题）；</li>
 *   <li>地址栏出现让人困惑的 “/continue”；</li>
 *   <li>实测在线环境：登录后 302 → /api/oauth2-auth/clients?continue → <b>403</b>。
 *       403 的真正根因不是 continue，而是库里 admin 没有 ROLE_ADMIN
 *       （GET /api/oauth2-auth/me 返回 authorities: []），而 /clients/** 要求 ROLE_ADMIN。
 *       角色映射由 sql/init-oauth2.sql 手动补齐。</li>
 * </ol>
 *
 * <h3>本处理器的策略（及原因）</h3>
 * <ul>
 *   <li><b>原始请求是 GET</b>（/authorize、/clients、/consent 等 auth-server 的主要场景）：
 *       直接 302 回原始 URL 并去掉 {@code continue} 标记。对 GET 来说等价于 RequestCache
 *       回放，但不会污染 OAuth2 参数、地址栏也更干净。</li>
 *   <li><b>原始请求是非 GET</b>（如会话过期后重提交的表单）：保留默认 {@code ?continue} +
 *       回放机制，因为只有回放才能还原原始请求体（POST body），直接 302 会丢表单数据。</li>
 *   <li><b>没有“原始请求”时</b>（用户直接打开登录页登录）：回落到 defaultSuccessUrl
 *       （/api/oauth2-auth/clients），而不是 Spring Security 默认的 {@code /}
 *       ——默认的 / 在网关上没有路由会 404，落到客户端管理页才是 auth-server 独立使用的正确入口。</li>
 * </ul>
 */
public class AuthServerLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();  // 从 Session 读取"被拦截的原始请求"

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        // 登录成功回调：先看 Session 里有没有被拦截前想去的地址
        SavedRequest savedRequest = this.requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String targetUrl;
            if ("GET".equalsIgnoreCase(savedRequest.getMethod())) {
                // GET：直接回原始 URL，去掉 Spring Security 附加的 ?continue 空参数
                targetUrl = stripContinueParam(savedRequest.getRedirectUrl());
            } else {
                // 非 GET（表单重提交）：保留 ?continue，让 RequestCache 回放还原请求体
                targetUrl = savedRequest.getRedirectUrl();
            }
            getRedirectStrategy().sendRedirect(request, response, targetUrl);  // 302 到目标地址
            return;
        }
        // 直接打开登录页登录：走 defaultSuccessUrl（/api/oauth2-auth/clients）
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** 用 UriComponentsBuilder 精确移除 ?continue 参数，保留其余查询参数 */
    private String stripContinueParam(String url) {
        return UriComponentsBuilder.fromUriString(url)
                .replaceQueryParam("continue")  // 移除 continue（无值参数）
                .build(false)                   // false：不对已编码字符二次编码，保持 URL 原样
                .toUriString();
    }
}
