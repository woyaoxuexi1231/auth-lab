package com.lab.securityjwtauthserver.controller;

import com.lab.securityjwtauthserver.dto.LoginRequest;
import com.lab.securityjwtauthserver.dto.TokenResponse;
import com.lab.securityjwtauthserver.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 认证控制器 — 处理登录和用户信息查询。
 *
 * <p>与 SecurityConfig 中的过滤器链协作：
 * <ol>
 *   <li>AuthController 处理 POST /api/jwt/auth/login → 认证成功后签发 JWT</li>
 *   <li>客户端收到 JWT 后存储到 localStorage</li>
 *   <li>后续请求时 JwtAuthenticationFilter 从 Authorization 头提取 JWT 并恢复认证</li>
 *   <li>SecurityConfig 的授权规则决定是否允许访问</li>
 * </ol>
 *
 * <p>本模块采用单 Token 方案（无 Refresh Token），保持职责单一。</p>
 */
@Slf4j
@RestController                 // = @Controller + @ResponseBody（所有方法返回 JSON）
@RequestMapping("/api/jwt")     // 所有端点统一前缀
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager; // 认证管理器：验证用户名密码
    private final JwtUtil jwtUtil;                             // JWT 工具：生成 Token

    /** Access Token 有效期（毫秒）— 来自 application.yml，避免在代码中硬编码；响应中会换算成秒 */
    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    // ================================================================
    // POST /api/jwt/auth/login — 登录
    // ================================================================

    /**
     * 用户登录 — 验证用户名密码，签发 JWT。
     *
     * <p>请求体：
     * <pre>{@code
     * { "username": "admin", "password": "123456" }
     * }</pre>
     *
     * <p>认证流程：
     * <pre>
     * ① 创建未认证的 UsernamePasswordAuthenticationToken（2 参数构造）
     * ② authenticationManager.authenticate() → DaoAuthenticationProvider
     *    → UserDetailsService.loadUserByUsername() → 查 DB
     *    → BCryptPasswordEncoder.matches() → 比密码
     *    → 成功返回已认证 Authentication / 失败抛 BadCredentialsException
     * ③ JwtUtil.generateAccessToken() → 生成 JWT
     * ④ 返回 TokenResponse（token + tokenType + expiresIn）
     * </pre>
     */
    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest loginRequest) {
        log.info("收到登录请求: {}", loginRequest.getUsername());

        // 手工参数校验（无 validation 依赖）：空用户名/密码直接 400
        if (loginRequest.getUsername() == null || loginRequest.getUsername().isBlank()
                || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }

        // ① 创建未认证 Token — 2 参数构造 = setAuthenticated(false)
        UsernamePasswordAuthenticationToken unauthenticatedToken =
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),     // principal
                        loginRequest.getPassword()       // credentials
                );

        // ② 认证管理器执行认证
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(unauthenticatedToken);
        } catch (BadCredentialsException e) {
            log.warn("登录失败（密码错误）: {}", loginRequest.getUsername());
            throw e; // 向上抛出 → ExceptionTranslationFilter → 401
        }

        // ③ 获取认证后的 UserDetails
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // ④ 生成 JWT（单 Token 方案，有效期 1 小时）
        String token = jwtUtil.generateAccessToken(userDetails);

        // ⑤ 封装响应（有效期来自 application.yml 的 app.jwt.access-token-expiration，避免硬编码）
        //    配置值是毫秒，对外按 RFC 6749 惯例换算成"秒"——客户端普遍按秒解释 expiresIn
        TokenResponse tokenResponse = TokenResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .build();

        log.info("登录成功，JWT 已签发: {}", loginRequest.getUsername());
        return ResponseEntity.ok(tokenResponse);
    }

    // ================================================================
    // GET /api/jwt/profile — 获取当前用户信息（需认证）
    // ================================================================

    /**
     * 获取当前登录用户信息。
     *
     * <p>此时 SecurityContextHolder 中的 Authentication
     * 已被 JwtAuthenticationFilter 从 JWT 恢复，
     * 所以可以直接获取当前用户。</p>
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile() {
        // 从 SecurityContextHolder 获取当前认证信息
        // 能到达此处的请求必然已通过 JwtAuthenticationFilter 恢复认证（anyRequest().authenticated() 兜底）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // principal 是过滤器里放入的 UserDetails（JWT 过滤器 3 参数构造的第一个参数）
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        Map<String, Object> profile = new HashMap<>();
        profile.put("username", userDetails.getUsername());
        profile.put("authorities", userDetails.getAuthorities());  // 权限以数据库为准（JwtAuthenticationFilter 步骤⑨）

        log.info("用户 {} 查询个人信息", userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    // ================================================================
    // GET /api/jwt/public/hello — 公开接口（无需认证）
    // ================================================================

    /**
     * 公开接口 — 不需要 JWT 即可访问。
     *
     * <p>在 SecurityConfig 中配置了 permitAll()。
     * 用于验证 JWT 认证系统是否正常工作：
     * 这个能访问 → 服务器正常；
     * /api/jwt/profile 不能访问（401） → JWT 保护生效。</p>
     */
    @GetMapping("/public/hello")
    public ResponseEntity<Map<String, String>> publicHello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "这是一个公开接口，不需要认证！");  // permitAll() 放行，无需 JWT 即可访问
        response.put("hint", "先 POST /api/jwt/auth/login 获取 JWT，"
                + "然后在 Authorization 头中携带 Bearer Token 访问 /api/jwt/profile。");  // 教学提示：如何验证认证链路
        return ResponseEntity.ok(response);
    }
}
