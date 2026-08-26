package com.lab.securityoauth2client.controller;

import com.lab.securityoauth2client.dto.PendingOAuthInfo;
import com.lab.securityoauth2client.dto.RegisterRequest;
import com.lab.securityoauth2client.entity.LocalUser;
import com.lab.securityoauth2client.service.LocalUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本地认证控制器 — 注册、登录状态、OAuth 待绑定信息。
 */
@RestController
@RequestMapping("/api/oauth2-client")
@RequiredArgsConstructor
public class AuthController {

    private final LocalUserService localUserService;
    private final AuthenticationManager authenticationManager;

    /** 本地注册（不依赖 OAuth） */
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpServletRequest request) {
        // 参数基础校验：用户名密码必填
        if (isBlank(req.getUsername()) || isBlank(req.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        try {
            // 注册：唯一性检查 + BCrypt 加密入库（LocalUserService 内完成）
            LocalUser user = localUserService.register(req.getUsername(), req.getPassword(), req.getEmail());
            autoLogin(request, req.getUsername(), req.getPassword());  // 注册后自动登录（免二次登录）
            return ResponseEntity.ok(Map.of("success", true, "username", user.getUsername()));
        } catch (IllegalArgumentException e) {
            // 业务校验失败（用户名/邮箱重复）→ 400 提示
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 获取 session 中暂存的待绑定 OAuth 信息 */
    @GetMapping("/oauth-pending")
    public ResponseEntity<?> oauthPending(HttpSession session) {
        // OAuth 登录未绑定时，CustomOAuth2UserService/CustomOidcUserService 会把三方信息暂存到这里
        Object attr = session.getAttribute("PENDING_OAUTH_BINDING");
        // 有效期校验（10 分钟）：过期视为"无暂存"，并清除过期属性防止残留
        if (attr instanceof PendingOAuthInfo info && !info.isExpired()) {
            // 绑定页读取三方账号信息用于展示
            return ResponseEntity.ok(Map.of(
                    "provider", info.getProvider(),
                    "providerUsername", info.getProviderUsername() != null ? info.getProviderUsername() : "",
                    "email", info.getEmail() != null ? info.getEmail() : "",
                    "avatarUrl", info.getAvatarUrl() != null ? info.getAvatarUrl() : "",
                    "sessionId", session.getId()));
        }
        if (attr != null) {
            session.removeAttribute("PENDING_OAUTH_BINDING");  // 过期信息直接移除，避免复用
        }
        return ResponseEntity.ok(Map.of("empty", true, "sessionId", session.getId()));  // 无暂存信息
    }

    /** 获取当前登录状态 */
    @GetMapping("/auth/status")
    public ResponseEntity<?> status() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 判断"真正登录"：排除匿名认证（anonymousUser）
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true, "username", auth.getName(),
                    "authType", auth.getClass().getSimpleName()));  // 区分 OAuth2AuthenticationToken / UsernamePasswordAuthenticationToken
        }
        return ResponseEntity.ok(Map.of("authenticated", false));
    }

    /** 自动登录 — 注册后立即建立 Session */
    private void autoLogin(HttpServletRequest request, String username, String rawPassword) {
        // 手动执行认证：构造未认证 Token → AuthenticationManager 验证
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(username, rawPassword);
        Authentication auth = authenticationManager.authenticate(token);
        // 手动把认证结果写入 SecurityContext + Session（与登录过滤器完成的事等价）
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        // 关键：把 SecurityContext 存进 Session，后续请求才能从 Session 恢复登录态
        request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
