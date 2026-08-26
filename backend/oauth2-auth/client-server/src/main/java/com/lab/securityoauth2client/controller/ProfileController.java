package com.lab.securityoauth2client.controller;

import com.lab.securityoauth2client.entity.UserOauthBinding;
import com.lab.securityoauth2client.service.LocalUserService;
import com.lab.securityoauth2client.service.UserOauthBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户信息控制器 — 兼容 OAuth2 登录和表单登录。
 */
@RestController
@RequestMapping("/api/oauth2-client")
@RequiredArgsConstructor
public class ProfileController {

    private final LocalUserService localUserService;
    private final UserOauthBindingService bindingService;

    /** 获取当前用户完整信息（含已绑定三方账户） */
    @GetMapping("/profile")
    public Map<String, Object> profile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 防御性检查：排除匿名认证；正常情况下未登录请求已被过滤器链拦截
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            // 抛 ResponseStatusException 返回 401 语义（此前抛 RuntimeException 会被当作 500 内部错误）
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户未认证");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        if (authentication instanceof OAuth2AuthenticationToken token) {
            // OAuth2/OIDC 登录 → 从 attributes 读信息（登录时注入的 local_user 字段）
            OAuth2User oauth2User = token.getPrincipal();
            Map<String, Object> attrs = oauth2User.getAttributes();
            result.put("name", attrs.getOrDefault("local_username", oauth2User.getName()));
            result.put("email", attrs.get("email"));
            result.put("provider", token.getAuthorizedClientRegistrationId());  // 是哪个三方登录的
            result.put("authType", "oauth2");
            result.put("authorities", oauth2User.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
            Object localUserIdObj = attrs.get("local_user_id");
            if (localUserIdObj != null) {
                result.put("bindings", buildBindingList(toLong(localUserIdObj)));
                result.put("hasPassword", localUserService.hasPassword(toLong(localUserIdObj)));
            }
        } else {
            // 表单登录 → 从 local_user 表读信息
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                result.put("name", userDetails.getUsername());
                result.put("authType", "form");
                result.put("authorities", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
                var localUser = localUserService.findByUsername(userDetails.getUsername());
                if (localUser != null) {
                    result.put("email", localUser.getEmail());
                    result.put("localUserId", localUser.getId());
                    result.put("bindings", buildBindingList(localUser.getId()));
                    result.put("hasPassword", localUserService.hasPassword(localUser.getId()));
                }
            }
        }
        return result;
    }

    /** 公开接口 */
    @GetMapping("/public/hello")
    public Map<String, Object> hello() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "你好！这是一个公开接口，不需要认证即可访问。");
        // 教学观察点：未登录时 authentication 是匿名认证对象（anonymousUser）
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            result.put("loginStatus", "已登录");
            result.put("username", authentication.getName());
        } else {
            result.put("loginStatus", "未登录");
        }
        return result;
    }

    /** 组装绑定列表（转成前端友好的 Map） */
    private List<Map<String, Object>> buildBindingList(Long localUserId) {
        List<UserOauthBinding> bindings = bindingService.listByLocalUserId(localUserId);
        return bindings.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("provider", b.getProvider());
            m.put("providerUsername", b.getProviderUsername());
            m.put("email", b.getEmail());
            m.put("avatarUrl", b.getAvatarUrl());
            m.put("bindTime", b.getBindTime() != null ? b.getBindTime().toString() : null);
            return m;
        }).collect(Collectors.toList());
    }

    /** 数值字段统一转 Long（JSON 反序列化可能是 Integer 或 Long） */
    private Long toLong(Object obj) {
        if (obj instanceof Long l) return l;
        if (obj instanceof Integer i) return i.longValue();
        return null;
    }
}
