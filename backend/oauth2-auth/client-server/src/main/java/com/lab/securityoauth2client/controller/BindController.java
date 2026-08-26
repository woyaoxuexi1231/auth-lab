package com.lab.securityoauth2client.controller;

import com.lab.securityoauth2client.dto.PendingOAuthInfo;
import com.lab.securityoauth2client.entity.LocalUser;
import com.lab.securityoauth2client.entity.UserOauthBinding;
import com.lab.securityoauth2client.service.LocalUserService;
import com.lab.securityoauth2client.service.UserOauthBindingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth 绑定控制器 — 处理第三方账号的绑定/解绑。
 *
 * <p>三种绑定方式：
 * <ol>
 *   <li>注册新账户 + 绑定 — POST /bind/register</li>
 *   <li>登录已有账户 + 绑定 — POST /bind/login</li>
 *   <li>已登录用户直接绑定 — POST /bind/current</li>
 * </ol>
 *
 * <p>安全检查：解绑前确保至少保留一种登录方式（密码或其他绑定）。</p>
 */
@RestController
@RequestMapping("/api/oauth2-client")
@RequiredArgsConstructor
public class BindController {

    private final LocalUserService localUserService;
    private final UserOauthBindingService bindingService;
    private final AuthenticationManager authenticationManager;

    /** 注册新账户 + 绑定当前 OAuth */
    @PostMapping("/bind/register")
    public ResponseEntity<?> registerAndBind(@RequestBody Map<String, String> body,
                                             HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");

        if (isBlank(username) || isBlank(password)) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        // 读取 OAuth 流程暂存的待绑定信息（CustomOAuth2UserService 写入 session）
        PendingOAuthInfo pending = getPendingOAuth(request.getSession());
        if (pending == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "绑定会话已过期"));
        }

        try {
            // ① 注册本地用户（唯一性 + BCrypt）
            LocalUser user = localUserService.register(username, password, body.get("email"));
            // ② 建立三方 ↔ 本地绑定关系
            bindingService.bind(user.getId(), pending.getProvider(), pending.getProviderUserId(),
                    pending.getProviderUsername(), pending.getEmail(), pending.getAvatarUrl());
            // ③ 清理暂存信息，避免重复绑定
            request.getSession().removeAttribute("PENDING_OAUTH_BINDING");
            autoLogin(request, username, password);  // 绑定后直接登录
            return ResponseEntity.ok(Map.of("success", true, "username", username));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 登录已有账户 + 绑定 */
    @PostMapping("/bind/login")
    public ResponseEntity<?> loginAndBind(@RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");

        PendingOAuthInfo pending = getPendingOAuth(request.getSession());
        if (pending == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "绑定会话已过期"));
        }

        LocalUser user;
        try {
            user = localUserService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
            }
            // 认证用户名密码（AuthenticationManager → UserDetailsServiceImpl → BCrypt 比对）
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            // 认证成功 → 手动写入 SecurityContext + Session
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        // 绑定三方账号到已登录的本地用户
        bindingService.bind(user.getId(), pending.getProvider(), pending.getProviderUserId(),
                pending.getProviderUsername(), pending.getEmail(), pending.getAvatarUrl());
        request.getSession().removeAttribute("PENDING_OAUTH_BINDING");
        return ResponseEntity.ok(Map.of("success", true, "username", username));
    }

    /** Bind the pending OAuth account to the currently logged-in local user. */
    @PostMapping("/bind/current")
    public ResponseEntity<?> bindCurrent(HttpServletRequest request) {
        Long localUserId = getCurrentLocalUserId();  // 从当前认证中解析本地用户 ID
        if (localUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        PendingOAuthInfo pending = getPendingOAuth(request.getSession());
        if (pending == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "绑定会话已过期"));
        }
        bindingService.bind(localUserId, pending.getProvider(), pending.getProviderUserId(),
                pending.getProviderUsername(), pending.getEmail(), pending.getAvatarUrl());
        request.getSession().removeAttribute("PENDING_OAUTH_BINDING");
        return ResponseEntity.ok(Map.of("success", true, "message", "bind-ok"));
    }

    @GetMapping("/bind/list")
    public ResponseEntity<?> bindList() {
        Long localUserId = getCurrentLocalUserId();
        if (localUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        List<UserOauthBinding> bindings = bindingService.listByLocalUserId(localUserId);
        // 转成前端友好的 Map 列表（避免直接暴露实体）
        var list = bindings.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("provider", b.getProvider());
            m.put("providerUsername", b.getProviderUsername());
            m.put("email", b.getEmail());
            m.put("avatarUrl", b.getAvatarUrl());
            m.put("bindTime", b.getBindTime() != null ? b.getBindTime().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("bindings", list,
                "hasPassword", localUserService.hasPassword(localUserId)));  // 是否有密码（决定解绑限制）
    }

    /** 解绑 — 安全检查：至少保留一种登录方式 */
    @DeleteMapping("/bind/{id}")
    public ResponseEntity<?> unbind(@PathVariable("id") Long id) {
        Long localUserId = getCurrentLocalUserId();
        if (localUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        // 越权检查：只能操作自己的绑定记录（防止水平越权）
        UserOauthBinding binding = bindingService.findById(id);
        if (binding == null || !binding.getLocalUserId().equals(localUserId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此绑定"));
        }

        // 核心安全检查：只剩 1 个绑定且无密码 → 拒绝解绑（否则账号无法登录）
        int bindingCount = bindingService.countByLocalUserId(localUserId);
        boolean hasPassword = localUserService.hasPassword(localUserId);
        if (bindingCount <= 1 && !hasPassword) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "至少需要保留一种登录方式，请先设置密码"));
        }

        bindingService.unbind(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "解绑成功"));
    }

    // === 辅助方法 ===

    /** 读取 session 中的待绑定 OAuth 信息（无则返回 null；超时（10 分钟）视为无效并清除） */
    private PendingOAuthInfo getPendingOAuth(HttpSession session) {
        Object attr = session.getAttribute("PENDING_OAUTH_BINDING");
        if (attr instanceof PendingOAuthInfo info && !info.isExpired()) {
            return info;
        }
        if (attr != null) {
            session.removeAttribute("PENDING_OAUTH_BINDING");  // 过期信息直接移除，避免复用
        }
        return null;
    }

    /** 从当前认证对象解析本地用户 ID（兼容 OAuth2 登录与表单登录两种认证） */
    private Long getCurrentLocalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        // OAuth2 登录：本地用户 ID 在登录时注入了 attributes
        if (auth instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken token) {
            Object id = token.getPrincipal().getAttributes().get("local_user_id");
            if (id instanceof Long lid) return lid;
            if (id instanceof Integer iid) return iid.longValue();
            return null;
        }
        // 表单登录：按用户名查本地用户
        if (auth instanceof UsernamePasswordAuthenticationToken) {
            LocalUser user = localUserService.findByUsername(auth.getName());
            return user != null ? user.getId() : null;
        }
        return null;
    }

    /** 手动认证并写入 Session（注册/绑定成功后直接建立登录态） */
    private void autoLogin(HttpServletRequest request, String username, String rawPassword) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);  // 持久化登录态
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
