package com.lab.securityjwtauthserver.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT 工具类 — 封装 JJWT 库的 Token 生成、解析、验证。
 *
 * <p>JWT 结构（三段式，点分隔）：</p>
 * <pre>
 *   Header.Payload.Signature
 *   eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.xxx
 *   └─ Base64(alg+typ) ─┘ └─ Base64(claims) ─┘ └─ HMAC-SHA256(Header.Payload, secret) ─┘
 * </pre>
 *
 * <p>安全性说明：
 * <ul>
 *   <li>Payload 是 Base64 编码（非加密），任何人都能解码 — 不要放敏感信息</li>
 *   <li>Signature 保证完整性 — 篡改 Payload 会导致签名不匹配</li>
 *   <li>Token 签发后无法主动撤销（除非引入黑名单） — Opaque Token 模块解决了这个问题</li>
 * </ul>
 *
 * <p>本模块采用单 Token 方案（无 Refresh Token），保持职责单一：演示 JWT 格式本身。</p>
 */
@Slf4j
@Component  // Spring 组件 — 可被注入到 Filter 和 Controller
public class JwtUtil {

    // === 从 application.yml 注入配置 ===
    @Value("${app.jwt.secret}")
    private String secret;                              // HMAC-SHA256 签名密钥（Base64 编码）

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;                 // Token 有效期（毫秒），默认 3600000 = 1 小时

    // ================================================================
    // Token 生成
    // ================================================================

    /**
     * 生成访问令牌（Access Token）。
     *
     * <p>使用 JJWT Builder 链式构建，包含标准声明（sub, iat, exp, jti, iss, aud）
     * 和自定义声明（authorities）。</p>
     *
     * @param userDetails 认证成功后的用户信息（含用户名和权限）
     * @return 完整的 JWT 字符串（Header.Payload.Signature）
     */
    public String generateAccessToken(UserDetails userDetails) {
        // ① 提取权限列表 → 写入 JWT，后续过滤器可直接读取，不需要再查 DB
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)    // 取出权限字符串
                .collect(Collectors.toList());          // 收集为 List

        long now = System.currentTimeMillis();          // ② 当前时间戳（毫秒）

        // ③ JJWT Builder 流式构建
        String token = Jwts.builder()
                .subject(userDetails.getUsername())     // sub：用户标识（核心声明）
                .claim("authorities", authorities)      // 自定义声明：权限列表
                .claim("aud", "security-lab")           // aud：受众
                .issuer("security-jwt-auth-server")     // iss：签发者
                .issuedAt(new Date(now))                // iat：签发时间
                .expiration(new Date(now + accessTokenExpiration)) // exp：过期时间
                .id(UUID.randomUUID().toString())       // jti：JWT 唯一 ID（防重放）
                .signWith(getSigningKey())              // ④ HMAC-SHA256 签名
                .compact();                             // ⑤ 序列化为最终的 JWT 字符串

        return token;
    }

    // ================================================================
    // Token 解析
    // ================================================================

    /**
     * 从 JWT 的 sub（Subject）声明中提取用户名。
     *
     * <p>parseToken() 内部会验证签名 — 如果签名不匹配，JJWT 抛异常。</p>
     */
    public String extractUsername(String token) {
        return parseToken(token).getSubject();          // getSubject() = 读取 sub 字段
    }

    /**
     * 从已解析的 Claims 提取权限（避免重复验签；过滤器中应复用一次解析结果）。
     *
     * @return GrantedAuthority 列表（用于构建 Authentication）
     */
    public List<GrantedAuthority> extractAuthorities(Claims claims) {
        // ① 读取自定义 authorities 字段（List<String>）
        List<?> rawList = claims.get("authorities", List.class);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();             // 无权限 → 返回空列表
        }

        // ② 将每个权限字符串包装为 SimpleGrantedAuthority
        return rawList.stream()
                .map(Object::toString)
                .map(SimpleGrantedAuthority::new)       // Spring Security 的权限实现
                .collect(Collectors.toList());
    }

    /**
     * 从 JWT 的自定义声明中提取权限列表。
     *
     * <p>在 JwtAuthenticationFilter 中，验证 JWT 通过后需要重建 Authentication 对象。
     * Authentication 需要包含权限信息，这些信息在 Token 生成时已写入。</p>
     *
     * @return GrantedAuthority 列表（用于构建 Authentication）
     */
    public List<GrantedAuthority> extractAuthorities(String token) {
        return extractAuthorities(parseToken(token));   // 原逻辑委托：解析一次后复用 Claims 提取
    }

    // ================================================================
    // Token 验证
    // ================================================================

    /**
     * 验证 JWT 是否有效。
     *
     * <p>两个条件必须同时满足：
     * <ol>
     *   <li>JWT 中的用户名与 UserDetails 一致（防止 Token 伪造）</li>
     *   <li>JWT 未过期</li>
     * </ol>
     * 签名验证已在 parseToken() 中自动完成。</p>
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);       // 从 JWT 取用户名
        boolean usernameMatch = username.equals(userDetails.getUsername()); // 比对
        boolean notExpired = !isTokenExpired(token);    // 检查过期

        return usernameMatch && notExpired;             // 两个条件都满足才有效
    }

    /**
     * 检查 JWT 是否过期。
     *
     * <p>JJWT 在解析时会自动检查 exp 声明 — 过期时抛 ExpiredJwtException，
     * 我们捕获异常来判断过期状态。同时做一次显式的过期时间比对作为双重检查。</p>
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);          // 尝试解析
            // 双重检查：显式比对过期时间和当前时间
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;                                // 捕获过期异常 → 确认已过期
        }
    }

    /**
     * 解析 JWT 并返回 Claims。
     *
     * <p>这是 JWT 验证的核心方法。JJWT 在解析时自动执行：
     * <ol>
     *   <li>签名验证 — 使用配置的密钥验证 HMAC-SHA256 签名</li>
     *   <li>过期验证 — 检查 exp 声明</li>
     *   <li>格式验证 — 检查是否为合法 JWT 格式（三段式）</li>
     * </ol>
     *
     * <p>可能抛出的异常：
     * <ul>
     *   <li>SignatureException — 签名无效（密钥不匹配或被篡改）</li>
     *   <li>ExpiredJwtException — Token 已过期</li>
     *   <li>MalformedJwtException — 格式错误</li>
     * </ul>
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())            // ① 设置签名验证密钥
                .build()                                // ② 构建解析器
                .parseSignedClaims(token)               // ③ 解析 + 验证签名 + 检查过期
                .getPayload();                          // ④ 返回 Payload（Claims）
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private volatile SecretKey signingKey;   // 缓存密钥对象，避免每次解析都 Base64 解码重建

    /**
     * 将 Base64 编码的密钥字符串转为 HMAC-SHA256 密钥对象。
     *
     * <p>密钥要求至少 256 位（32 字节），否则 JJWT 抛出 WeakKeyException。
     * Base64 编码使二进制密钥能以纯文本存储在配置文件中。
     * 结果缓存（volatile + 双重检查）— 每个请求多次解析 JWT 时不必反复 Base64 解码重建。</p>
     */
    private SecretKey getSigningKey() {
        SecretKey key = this.signingKey;
        if (key == null) {
            synchronized (this) {
                if (this.signingKey == null) {
                    byte[] keyBytes = Decoders.BASE64.decode(secret); // Base64 解码 → 字节数组
                    this.signingKey = Keys.hmacShaKeyFor(keyBytes);   // 创建 HMAC-SHA256 密钥
                }
                key = this.signingKey;
            }
        }
        return key;
    }
}
