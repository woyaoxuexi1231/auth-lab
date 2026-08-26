package com.lab.securityoauth2authserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2 单点登出控制器。
 *
 * <p>当 OAuth2 客户端登出时，需要在认证服务器也销毁 Session。
 * 通过弹窗 + postMessage 实现跨窗口通信，通知父窗口登出完成。</p>
 */
@RestController
public class SessionLogoutController {

    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /** 允许的登出回跳源地址（逗号分隔白名单，来自 application.yml） */
    @Value("${app.oauth2.allowed-logout-origins:}")
    private String allowedLogoutOrigins;

    /**
     * 登出弹窗 — 销毁认证服务器 Session，通过 postMessage 通知父窗口。
     *
     * <p>⚠ 安全提示：targetOrigin 来自请求参数并被嵌入返回的 HTML（postMessage 目标 + 跳转地址），
     * 已修复：必须命中白名单 {@code app.oauth2.allowed-logout-origins}，否则拒绝（开放重定向 + 反射型 XSS 防护）。</p>
     */
    @GetMapping(value = "/api/oauth2-auth/session/logout-popup", produces = "text/html;charset=UTF-8")
    public String logoutPopup(
            @RequestParam String targetOrigin,   // 客户端传入的"父窗口源地址"（白名单校验，见下方）
            HttpServletRequest request,
            HttpServletResponse response) {
        // ① 白名单校验：目标源不在白名单内直接 400，杜绝任意源跳转与 XSS 注入
        if (!isAllowedLogoutOrigin(targetOrigin)) {
            response.setStatus(400);
            return "Bad Request: targetOrigin not allowed";
        }

        // ② 销毁当前用户的 Session
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logoutHandler.logout(request, response, authentication);  // 使 Session 失效并清理 SecurityContext

        // ③ 返回 HTML — 通过 postMessage 通知 opener 窗口
        String safeOrigin = escapeJs(targetOrigin);  // 白名单校验后的双保险转义（防御 </script> 截断）
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"/><title>Auth Server Logout</title></head>
                <body>
                <script>
                  (function () {
                    var targetOrigin = '%s';
                    if (window.opener && !window.opener.closed) {
                      window.opener.postMessage({ type: 'oauth2-logout-result', status: 'success' }, targetOrigin);
                      window.close();
                      return;
                    }
                    window.location.href = targetOrigin + '/#/oauth2';
                  })();
                </script>
                </body>
                </html>
                """.formatted(safeOrigin);
    }

    /** 白名单校验：精确匹配，杜绝任意源跳转与 XSS 注入 */
    private boolean isAllowedLogoutOrigin(String origin) {
        if (!StringUtils.hasText(origin) || !StringUtils.hasText(allowedLogoutOrigins)) {
            return false;
        }
        for (String allowed : allowedLogoutOrigins.split(",")) {
            if (allowed.trim().equals(origin)) {
                return true;
            }
        }
        return false;
    }

    /** 防止 XSS — 转义反斜杠、单引号及尖括号（防 </script> 截断，白名单外的双保险） */
    private String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("<", "\\u003c").replace(">", "\\u003e");
    }
}
