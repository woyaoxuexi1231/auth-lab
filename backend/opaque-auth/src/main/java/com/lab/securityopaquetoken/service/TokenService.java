package com.lab.securityopaquetoken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Opaque Token 服务 — Token 的生成、验证、撤销。
 *
 * <p>这是 Opaque Token 认证的核心。与 JWT 的本质区别：
 * Token 本身不携带任何信息（只是一个随机 UUID），
 * 所有用户信息（用户名、权限）都存储在 Redis 中。</p>
 *
 * <p>Redis 存储结构：
 * <pre>
 * Key:   opaque:token:550e8400-e29b-41d4-a716-446655440000
 * Value: {"username":"admin","authorities":["ROLE_USER","ROLE_ADMIN"]}
 * TTL:   7200 秒（2 小时）
 * </pre>
 *
 * <p>与 JWT 对比：
 * <ul>
 *   <li>✅ Token 可随时撤销（删 Redis Key 即可）</li>
 *   <li>✅ 权限可实时更新（改 Redis Value）</li>
 *   <li>✅ Token 更短（36 字符 UUID vs 200+ 字符 JWT）</li>
 *   <li>❌ 每次请求需查 Redis（JWT 本地验签更快）</li>
 *   <li>❌ 服务器有状态（依赖 Redis）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;    // Redis 操作模板
    private final ObjectMapper objectMapper;            // Jackson JSON 序列化

    @Value("${app.token.expire-seconds}")
    private long expireSeconds;                          // Token 有效期（秒），默认 7200

    @Value("${app.token.redis-prefix}")
    private String redisPrefix;                          // Redis Key 前缀，默认 "opaque:token:"

    /**
     * 创建 Token — 登录成功后调用。
     *
     * <p>流程：
     * ① UUID.randomUUID() 生成随机 Token
     * ② TokenInfo → Jackson 序列化为 JSON
     * ③ 写入 Redis：SET opaque:token:{uuid} "{json}" EX {expireSeconds}
     * ④ 返回 UUID 字符串给客户端</p>
     */
    public String createToken(String username, List<String> authorities) {
        // ① 生成随机 UUID（36 字符，如 "550e8400-e29b-41d4-a716-446655440000"）
        // UUID 不可猜测/不可逆向推导，天然防伪造——这就是"不透明"的含义
        String token = UUID.randomUUID().toString();

        // ② 构建 Redis Value — Jackson 序列化 TokenInfo 为 JSON
        String value;
        try {
            value = objectMapper.writeValueAsString(new TokenInfo(username, authorities));
        } catch (Exception e) {
            throw new RuntimeException("序列化 Token 信息失败", e);  // 序列化失败属内部错误，直接抛出（配置问题导致，用户无法自行解决）
        }

        // ③ 写入 Redis — SET + EX（设置过期时间）
        String redisKey = redisPrefix + token;
        // 带 TTL 写入：过期后 Redis 自动删除 Key，Token 自然失效（无需后台清理任务）
        redisTemplate.opsForValue().set(redisKey, value, expireSeconds, TimeUnit.SECONDS);

        log.info("Token 创建成功: {} → 用户={}", token, username);
        return token;
    }

    /**
     * 验证 Token — 每次请求时由过滤器调用。
     *
     * <p>流程：
     * ① GET opaque:token:{uuid}
     * ② 有值 → Token 有效 → Jackson 反序列化为 TokenInfo
     * ③ 无值 → Token 无效/过期 → 返回 null</p>
     *
     * <p>注意：这里只查 Redis，不刷新 TTL。
     * 如需"滑动过期"效果，可在此处调用 redisTemplate.expire()。</p>
     */
    public TokenInfo getTokenInfo(String token) {
        String redisKey = redisPrefix + token;
        String value = redisTemplate.opsForValue().get(redisKey);   // GET key — 存在即有效，不存在即失效

        if (value == null) {
            return null;    // Key 不存在 → Token 无效或已过期
        }

        try {
            return objectMapper.readValue(value, TokenInfo.class);  // JSON → TokenInfo（还原用户名与权限）
        } catch (Exception e) {
            log.error("反序列化 Token 信息失败: {}", e.getMessage());
            return null;  // 解析失败按无效处理，不让脏数据进入认证流程
        }
    }

    /**
     * 撤销 Token — 登出时调用。直接 DEL Redis Key。
     *
     * <p>这就是 Opaque Token 相比 JWT 的最大优势：
     * JWT 签出后到过期前仍有效（无法撤销），Opaque Token 删 Redis 后立即失效。</p>
     */
    public void revokeToken(String token) {
        String redisKey = redisPrefix + token;
        Boolean deleted = redisTemplate.delete(redisKey);           // DEL key — 删除后同一 UUID 再也查不到，立即失效
        log.info("Token 已撤销: {} → {}", token, deleted);
    }

    /**
     * Token 信息 — 序列化为 JSON 存入 Redis。
     *
     * <p>字段为 public 以兼容 Jackson 序列化/反序列化。
     * 需要 public 无参构造（Jackson 反序列化用）。</p>
     */
    public static class TokenInfo {
        public String username;
        public List<String> authorities;

        public TokenInfo() {}   // Jackson 反序列化需要无参构造

        public TokenInfo(String username, List<String> authorities) {
            this.username = username;
            this.authorities = authorities;
        }

        /** 将权限字符串列表转为 Spring Security GrantedAuthority 列表 */
        public List<SimpleGrantedAuthority> toGrantedAuthorities() {
            if (authorities == null) return List.of();
            return authorities.stream()
                    .map(SimpleGrantedAuthority::new)  // 每个权限字符串包装为认证对象可识别的授权单元
                    .collect(Collectors.toList());
        }
    }
}
