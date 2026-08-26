package com.lab.securityoauth2client.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 首页控制器 — 纯服务端渲染的 HTML 页面（独立于 Vue SPA）。
 *
 * <p>展示登录状态、OAuth2 流程架构图、接口导航链接。
 * 内联 CSS 使这个页面可以完全独立工作，不依赖前端构建系统。</p>
 */
@Controller
public class HomeController {

    @GetMapping("/")
    @ResponseBody
    public String home() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isLoggedIn = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>OAuth2 Client Demo</title>\n");
        html.append("  <style>\n");
        html.append("    * { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; background: #f0f2f5; }\n");
        html.append("    .card { background: white; border-radius: 12px; padding: 24px; margin: 16px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }\n");
        html.append("    .success { border-left: 4px solid #4CAF50; }\n");
        html.append("    .warning { border-left: 4px solid #FF9800; }\n");
        html.append("    .info { border-left: 4px solid #2196F3; }\n");
        html.append("    h1 { color: #333; font-size: 1.5rem; }\n");
        html.append("    .btn { display: inline-block; padding: 10px 20px; border-radius: 8px; color: white; text-decoration: none; font-weight: 500; margin: 5px 8px 5px 0; font-size: 14px; }\n");
        html.append("    .btn-lab { background: #2563eb; }\n");
        html.append("    .btn-github { background: #24292e; }\n");
        html.append("    .btn-google { background: #ea4335; }\n");
        html.append("    .btn-logout { background: #64748b; }\n");
        html.append("    code { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; }\n");
        html.append("    pre { background: #1e293b; color: #e2e8f0; padding: 15px; border-radius: 8px; overflow-x: auto; font-size: 0.85em; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <h1>Security OAuth2 Client - OAuth2客户端演示</h1>\n");

        if (isLoggedIn) {
            String provider = "";
            if (authentication instanceof OAuth2AuthenticationToken token) {
                provider = token.getAuthorizedClientRegistrationId();
            }
            html.append("  <div class=\"card success\">\n");
            html.append("    <h2>登录状态：已登录</h2>\n");
            html.append("    <p>认证提供方：<strong>").append(provider).append("</strong></p>\n");
            html.append("    <p>用户名：<strong>").append(authentication.getName()).append("</strong></p>\n");
            html.append("    <p>权限：\n");
            authentication.getAuthorities().forEach(auth ->
                    html.append("      <code>").append(auth.getAuthority()).append("</code> "));
            html.append("    </p>\n");
            // 登出仅接受 POST（防 <img> 等 GET 链接触发登出）；CSRF 已禁用，POST 表单可正常提交
            html.append("    <form method=\"post\" action=\"/api/oauth2-client/logout\" style=\"display:inline\">\n");
            html.append("      <button type=\"submit\" class=\"btn btn-logout\">退出登录</button>\n");
            html.append("    </form>\n");
            html.append("  </div>\n");
        } else {
            html.append("  <div class=\"card warning\">\n");
            html.append("    <h2>登录状态：未登录</h2>\n");
            html.append("    <a class=\"btn btn-lab\" href=\"/api/oauth2-client/authorization/lab-client\">Lab 认证服务器登录</a>\n");
            html.append("    <a class=\"btn btn-github\" href=\"/api/oauth2-client/authorization/github\">GitHub 登录</a>\n");
            html.append("    <a class=\"btn btn-google\" href=\"/api/oauth2-client/authorization/google\">Google 登录</a>\n");
            html.append("  </div>\n");
        }

        html.append("  <div class=\"card info\">\n");
        html.append("    <h2>接口导航</h2>\n");
        html.append("    <a href=\"/api/oauth2-client/public/hello\">/api/oauth2-client/public/hello</a> — 公开接口<br>\n");
        html.append("    <a href=\"/api/oauth2-client/profile\">/api/oauth2-client/profile</a> — 当前用户信息<br>\n");
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }
}
