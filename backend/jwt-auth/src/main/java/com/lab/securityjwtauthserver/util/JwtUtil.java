package com.lab.securityjwtauthserver.util;

import io.jsonwebtoken.Claims;
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
 *   <li>验签时同时强制校验 iss / aud — 挡住"同一密钥签发给别人"的 Token</li>
 *   <li>Token 签发后无法主动撤销（除非引入黑名单） — Opaque Token 模块解决了这个问题</li>
 * </ul>
 *
 * <p>关于"哪些声明必填"（常见误解澄清，详见 docs/auth-principles/JWT认证原理.md 的 2.1）：
 * <ul>
 *   <li>RFC 7519 §4.1 注册的声明<b>没有一个是强制的</b>（原文：None of the claims defined below
 *       are intended to be mandatory…）。本类之所以有 {@code subject()} / {@code expiration()}
 *       这类类型化方法，是因为这些<b>名字、类型、语义被注册</b>了，不是因为它们必须出现。</li>
 *   <li>"必填"只有两个来源：① 校验端 {@code require} 了什么 —— 见 {@link #parseToken(String)}
 *       中的 {@code requireIssuer} / {@code requireAudience}；② 所遵循的 profile
 *       （OIDC 身份令牌要求 iss/sub/aud/exp/iat，RFC 9068 访问令牌要求 iss/exp/aud/sub/client_id/iat/jti）。</li>
 *   <li>本项目把 sub / iss / aud / exp 全部填满，是<b>主动选择</b>（原因见
 *       {@link #generateAccessToken(UserDetails)} 的逐行注释），不是规范要求。</li>
 * </ul>
 * 一句话：<b>规范规定字段"怎么写"，校验端规定哪些"必须写"；require 了的才真的必填。</b></p>
 *
 * <p>本模块采用单 Token 方案（无 Refresh Token），保持职责单一：演示 JWT 格式本身。</p>
 */
@Slf4j
@Component  // Spring 组件 — 可被注入到 Filter 和 Controller
public class JwtUtil {

    // === 构造期注入即固化（final），运行期不再变化 ===
    private final SecretKey signingKey;         // HMAC-SHA256 签名 / 验签密钥
    private final String issuer;                // iss：签发者标识（签发时写入，验签时强制校验）
    private final String audience;              // aud：受众标识（同上）
    private final long accessTokenExpiration;   // Token 有效期（毫秒）

    /**
     * 构造期完成配置读取与密钥构建。
     *
     * <p>把 Base64 解码 + 密钥构建放在构造期而不是首次使用时：
     * 密钥格式错误或长度不足 256 位会直接导致<b>应用启动失败</b>（fail fast），
     * 而不是等第一个请求才抛 {@code WeakKeyException}；
     * 同时密钥成为 final 字段，免去了运行期懒加载的并发处理（原本用 volatile + 双重检查）。</p>
     */
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.issuer}") String issuer,
                   @Value("${app.jwt.audience}") String audience,
                   @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    // ================================================================
    // Token 生成
    // ================================================================

    /**
     * 生成访问令牌（Access Token）。
     *
     * <p>使用 JJWT Builder 链式构建，包含标准声明（sub, iss, aud, iat, exp, jti）
     * 和自定义声明（authorities）。每个声明的"必填 / 建议"标注及原因见方法内逐行注释。</p>
     *
     * @param userDetails 认证成功后的用户信息（含用户名和权限）
     * @return 完整的 JWT 字符串（Header.Payload.Signature）
     */
    public String generateAccessToken(UserDetails userDetails) {
        // ① 提取权限列表 → 写入 JWT
        //    注意：这只是"签发时刻的快照"，过滤器恢复认证时以数据库权限为准
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)    // 取出权限字符串
                .collect(Collectors.toList());          // 收集为 List

        long now = System.currentTimeMillis();          // ② 当前时间戳（毫秒）

        // ③ JJWT Builder 流式构建
        //    ── 声明必填判据（详见类注释）──
        //    规范（RFC 7519）一个都不强制；"必填"只来自两处：
        //      a) 校验端的 require —— 见 parseToken() 的 requireIssuer / requireAudience
        //      b) 你遵循的 profile —— OIDC / RFC 9068
        //    下面这几条是本项目按"安全 + 运维"需要主动填满的：
        //      缺 exp → 永不过期的凭据（永久后门）    缺 iss/aud → 无法判断"谁签给谁"
        //      （A 系统签的 Token 能拿去打 B 系统）   缺 sub → 校验端不知道"这是谁"
        String token = Jwts.builder()
                .subject(userDetails.getUsername())     // sub 必填：主体（这是谁）
                .claim("authorities", authorities)      // 自定义：权限快照（签发时刻；过滤器以 DB 为准）
                .audience().add(audience).and()         // aud 必填：受众（签给谁；验签端 requireAudience 强制比对）
                .issuer(issuer)                         // iss 必填：签发者（验签端 requireIssuer 强制比对）
                .issuedAt(new Date(now))                // iat 建议：签发时间（排查问题 / 算凭据年龄）
                .expiration(new Date(now + accessTokenExpiration)) // exp 必填：过期时间；JJWT 解析时自动校验
                .id(UUID.randomUUID().toString())       // jti 建议：唯一 ID — 日志追踪 / 将来接黑名单；
                                                        //   注意它本身并不防重放，防重放要服务端存已用 jti（本模块未做）
                .signWith(signingKey, Jwts.SIG.HS256)   // ④ 显式指定 HS256，签名算法不由 Token 自述决定
                .compact();                             // ⑤ 序列化为最终的 JWT 字符串

        return token;
    }

    // ================================================================
    // Token 解析 / 验证
    // ================================================================

    /**
     * 解析 JWT 并返回 Claims。
     *
     * <p>这是 JWT 验证的核心方法。JJWT 在解析时自动执行：
     * <ol>
     *   <li>签名验证 — 用配置的密钥验证 HMAC-SHA256 签名</li>
     *   <li>过期验证 — 检查 exp 声明（过期抛 ExpiredJwtException）</li>
     *   <li>格式验证 — 检查是否为合法 JWT 格式（三段式）</li>
     *   <li>声明校验 — iss / aud 必须与本服务配置一致（requireIssuer / requireAudience）</li>
     * </ol>
     *
     * <p>可能抛出的异常（调用方统一按"Token 无效"处理）：
     * <ul>
     *   <li>SignatureException — 签名无效（密钥不匹配或被篡改）</li>
     *   <li>ExpiredJwtException — Token 已过期</li>
     *   <li>MalformedJwtException — 格式错误</li>
     *   <li>IncorrectClaimException — iss / aud 不匹配</li>
     * </ul>
     */
    public Claims parseToken(String token) {
        // ★ requireIssuer / requireAudience 就是"把 iss / aud 变成必填"的开关：
        //   Token 里缺这两个声明 → MissingClaimException；值不匹配 → IncorrectClaimException。
        //   即"哪些声明必填"是校验端在【这里】决定的，而不是签发端决定的（见类注释）。
        return Jwts.parser()
                .verifyWith(signingKey)                 // ① 设置签名验证密钥
                .requireIssuer(issuer)                  // ② iss 必填且必须匹配：挡住其他系统用同一密钥签发的 Token
                .requireAudience(audience)              // ③ aud 必填且必须匹配：挡住签发给其他受众的 Token
                .build()                                // ④ 构建解析器
                .parseSignedClaims(token)               // ⑤ 解析 + 验证签名 + 检查过期 + 校验 iss/aud
                .getPayload();                          // ⑥ 返回 Payload（Claims）
    }

    /**
     * 从已解析的 Claims 提取权限（避免重复验签；调用方应复用一次解析结果）。
     *
     * <p>返回的是 Token 内嵌的<b>权限快照</b>。当前过滤器以数据库权限为准，
     * 仅在 DEBUG 日志里用它与 DB 权限比对，用于观察"快照会过期"这一现象。</p>
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
}
