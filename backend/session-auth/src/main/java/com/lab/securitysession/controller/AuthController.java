package com.lab.securitysession.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session 认证控制器 — 用户信息、Session 详情、公开接口。
 *
 * <p>与 JWT 模块的关键区别：
 * SecurityContext 是从 HttpSession 中"恢复"的，而不是每次请求从 JWT 中"重建"的。
 * 这意味着后续请求不需要重新查数据库验证密码。</p>
 *
 * <p>Session 生命周期关键时间点：
 * <ul>
 *   <li>creationTime — Session 创建时间（即登录成功时间）</li>
 *   <li>lastAccessedTime — 最后访问时间，每次请求更新</li>
 *   <li>maxInactiveInterval — 最大不活动时间（超时自动过期）</li>
 *   <li>过期条件：lastAccessedTime + maxInactiveInterval < 当前时间</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/session")
public class AuthController {

    /**
     * 获取当前登录用户信息。
     *
     * <p>前提：SecurityContextPersistenceFilter 已从 HttpSession/Redis 恢复了 SecurityContext。
     * 如果用户未登录，authentication 为 AnonymousAuthenticationToken。</p>
     */
    @GetMapping("/profile")
    public Map<String, Object> profile(HttpSession session) {
        // 从线程上下文取认证对象：登录后 Spring Security 会在每次请求开始时
        // 通过 SecurityContextHolderFilter 从 Session 恢复，存于当前线程中
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> result = new LinkedHashMap<>();  // 用 LinkedHashMap 保证字段输出顺序稳定，便于阅读
        result.put("message", "Session认证成功！当前用户信息如下：");

        if (isReallyAuthenticated(authentication)) {  // 排除匿名认证：AnonymousAuthenticationToken.isAuthenticated() 恒为 true
            result.put("username", authentication.getName());  // 当前登录用户名（来自 UserDetails 的 username）
            result.put("authorities", authentication.getAuthorities());  // 角色/权限集合（来自 role 表查询结果）
            result.put("authenticated", authentication.isAuthenticated());  // 认证状态标记
        }

        result.put("sessionId", session.getId());       // 即 Cookie 中 JSESSIONID_LAB 的值（服务端唯一会话标识）
        result.put("sessionCreationTime",
                LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getCreationTime()),
                        ZoneId.systemDefault()));  // Session 创建时间（毫秒时间戳 → 本地时间，即登录成功时刻）
        return result;
    }

    /**
     * 查看 Session 详细信息 — 帮助理解 Session 生命周期。
     *
     * <p>Redis 中 Session Key 的 TTL 会在每次访问时刷新。
     * 只要用户持续使用，Session 不会过期；停止使用超过 maxInactiveInterval 后 Redis 自动删除。</p>
     */
    @GetMapping("/info")
    public Map<String, Object> sessionInfo(HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getId());
        result.put("creationTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(session.getCreationTime()), ZoneId.systemDefault()));  // 会话创建时刻
        result.put("lastAccessedTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(session.getLastAccessedTime()), ZoneId.systemDefault()));  // 最近一次请求时刻（每次请求刷新）
        result.put("maxInactiveIntervalSeconds", session.getMaxInactiveInterval());  // 闲置超时秒数（来自 application.yml: 30m）
        result.put("isNew", session.isNew());  // 是否本次请求新建的 Session

        // 再次读取认证信息，展示"当前用户与 Session 的关系"
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            result.put("currentUser", auth.getName());
            result.put("authorities", auth.getAuthorities());
        }

        // 教学提示：Redis 中该 Session 的实际 Key 前缀来自 spring.session.redis.namespace
        result.put("redisLookupHint", "在 redis-cli 中执行: GET spring:session:lab:sessions:"
                + session.getId());
        return result;
    }

    /**
     * 公开接口 — 无需认证。
     * 即使未登录也能访问，此时 SecurityContext 中的 authentication 是 Anonymous。
     */
    @GetMapping("/public/hello")
    public Map<String, Object> publicHello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "这是一个公开接口，无需登录即可访问");

        // 教学观察点：未登录时这里拿到的其实是 AnonymousAuthenticationToken
        //（其 isAuthenticated() 恒为 true，必须用 isReallyAuthenticated 排除，否则未登录也误判为"已认证"）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        result.put("currentAuthentication", auth != null ? auth.getName() : "null");  // 未登录时值为 "anonymousUser"
        result.put("isAuthenticated", isReallyAuthenticated(auth));

        return result;
    }

    /** 判断是否"真正登录"：AnonymousAuthenticationToken.isAuthenticated() 恒为 true，必须显式排除 */
    private boolean isReallyAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}
