package com.lab.securityopaquetoken.controller;

import com.lab.securityopaquetoken.dto.LoginRequest;
import com.lab.securityopaquetoken.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Opaque Token 认证控制器 — 登录、登出、用户信息。
 *
 * <p>与 JWT 模块 AuthController 的关键区别：
 * <ul>
 *   <li>登录返回 UUID（36 字符）而非 JWT（200+ 字符）</li>
 *   <li>登出真正删除 Redis Key（JWT 无法撤销，只能前端删除）</li>
 *   <li>登录使用 LoginRequest DTO 接收（字段名拼写错误编译期可发现）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/opaque")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;  // 认证管理器
    private final TokenService tokenService;                    // Token 服务（Redis 读写）

    @Value("${app.token.expire-seconds}")
    private long expireSeconds;                                  // Token 有效期（秒）

    /**
     * 登录 — 返回 Opaque Token（UUID）。
     *
     * <p>流程：
     * ① 接收 JSON {username, password}
     * ② AuthenticationManager 验证 → 成功获取 authorities
     * ③ TokenService.createToken(username, authorities) → 生成 UUID + 写 Redis
     * ④ 返回 {token, tokenType, expiresIn, username}</p>
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        // 手工参数校验（无 validation 依赖）：空用户名/密码直接 400
        if (loginRequest.getUsername() == null || loginRequest.getUsername().isBlank()
                || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和密码不能为空"));
        }
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        try {
            // ① 验证用户名密码
            // 手动构造未认证 Token → 交给 AuthenticationManager（内部走 DaoAuthenticationProvider → UserDetailsServiceImpl）
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // ② 提取权限（如 ROLE_USER、ROLE_ADMIN），稍后随用户信息一起写入 Redis
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)  // 权限对象 → 纯字符串
                    .collect(Collectors.toList());

            // ③ 创建 Opaque Token（UUID）并写入 Redis（SET + TTL）
            String token = tokenService.createToken(username, authorities);

            // ④ 返回响应（客户端后续请求携带 Authorization: Bearer <token>）
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);             // UUID，非 JWT
            result.put("tokenType", "Bearer");
            result.put("expiresIn", expireSeconds);  // 服务端告知前端有效期，便于前端提前引导重新登录
            result.put("username", username);

            return ResponseEntity.ok(result);
        } catch (BadCredentialsException e) {
            // 凭证错误（含查无用户 — DaoAuthenticationProvider 默认 hideUserNotFoundExceptions=true 会统一转成此异常）→ 401
            log.warn("登录失败（凭证错误）: {}", username);
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户名或密码错误"));
        } catch (Exception e) {
            // ★ 系统异常与凭证错误分离，便于排障（此前全被 catch 成 401）
            log.error("登录接口异常（非凭证错误）", e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "服务器内部错误"));
        }
    }

    /**
     * 登出 — 从 Redis 删除 Token。
     *
     * <p>★ 这是 Opaque Token 相较 JWT 的核心优势：
     * JWT 登出后 Token 在过期前仍然有效（无法撤销），
     * Opaque Token 登出后 Redis Key 被删除，Token 立即失效。</p>
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);  // 提取 UUID
            tokenService.revokeToken(token);                    // DEL Redis Key → Token 即刻作废
        }
        SecurityContextHolder.clearContext();                   // 清除当前线程认证信息（STATELESS 下作用有限，属防御性操作）
        return ResponseEntity.ok(Map.of("code", 200, "message", "已登出"));
    }

    /** 获取当前用户信息 */
    @GetMapping("/profile")
    public ResponseEntity<?> profile() {
        // 从当前线程上下文取认证对象：由 TokenAuthenticationFilter 在请求开始时注入
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 双保险：正常情况下无 Token 请求会被过滤器链 401 拦截，此处再防御一次
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("username", auth.getName());  // 用户名（来自 Redis 中存储的 TokenInfo）
        result.put("authorities", auth.getAuthorities());  // 权限（同样来自 Redis，可实时修改）
        result.put("_comment", "与 JWT 不同，这里的用户信息是从 Redis 查询的，"
                + "不是从 Token 中解析的。Token 本身只是一个 UUID。");
        return ResponseEntity.ok(result);
    }

    /** 公开接口 — 无需认证 */
    @GetMapping("/public/hello")
    public ResponseEntity<?> publicHello() {
        return ResponseEntity.ok(Map.of(
                "message", "这是一个公开接口，不需要认证",
                "tokenType", "Opaque Token（UUID，非 JWT）"
        ));
    }
}
