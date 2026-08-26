package com.lab.securityoauth2resourceserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源服务器示例接口。
 *
 * <p>所有受保护接口依赖 BearerTokenAuthenticationFilter 自动完成 JWT 验签，
 * Controller 中通过 {@code @AuthenticationPrincipal Jwt jwt} 直接获取已验证的 JWT。</p>
 */
@RestController
@RequestMapping("/api/oauth2-resource")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    /** 公开接口 — 无需认证 */
    @GetMapping("/public/info")
    public Map<String, Object> publicInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> result = new HashMap<>();
        result.put("service", "Security OAuth2 Resource Server");
        // 教学观察点：未认证请求也能到达这里，authentication 是匿名对象
        result.put("authenticated", auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal()));
        return result;
    }

    /** 需要 SCOPE_read — 返回资源列表 */
    @PreAuthorize("hasAuthority('SCOPE_read')")  // 方法级鉴权：与 SecurityConfig 的 hasAuthority 双重防护
    @GetMapping("/items")
    public List<Map<String, Object>> list(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        // @AuthenticationPrincipal 直接把过滤器验签后的 JWT 注入（含所有 claims）
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(1L, "OAuth2 入门指南", "SECURITY", jwt.getSubject()));  // sub = 资源访问者标识
        items.add(createItem(2L, "资源服务器接口联调", "INTEGRATION", jwt.getSubject()));
        log.info("返回列表: subject={}, size={}", jwt.getSubject(), items.size());
        return items;
    }

    /** 组装列表项 */
    private Map<String, Object> createItem(Long id, String title, String category, String user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("category", category);
        item.put("currentUser", user);  // 标注数据所属用户（演示用）
        return item;
    }
}
