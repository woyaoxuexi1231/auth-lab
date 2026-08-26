package com.lab.securityoauth2authserver.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OIDC 用户信息 + 公开 API 控制器。
 *
 * <p>GET /api/oauth2-auth/me — 自定义调试端点。</p>
 * <p>GET /api/oauth2-auth/public/hello — 公开接口。</p>
 * <p>注意：/api/oauth2-auth/userinfo 由授权服务器 OIDC 协议端点处理（OidcUserInfoEndpointFilter），
 * 自定义 Controller 同路径不可达；如需自定义 claims 请改用 OidcUserInfoMapper（见评审报告 §八-1）。</p>
 */
@RestController
@RequestMapping("/api/oauth2-auth")
public class UserInfoController {

    /** 公开接口 — 无需认证，返回 OAuth2 端点列表 */
    @GetMapping("/public/hello")
    public Map<String, Object> publicHello() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Hello from OAuth2 Authorization Server!");
        result.put("server", "OAuth2认证服务器");
        return result;
    }

    /** 当前用户信息（调试用） */
    @GetMapping("/me")
    public Map<String, Object> currentUser() {
        // 浏览器会话登录场景下读取当前认证（客户端管理页调试用）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> result = new HashMap<>();
        if (authentication != null && authentication.isAuthenticated()) {
            result.put("username", authentication.getName());
            result.put("authorities", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        }
        return result;
    }
}
