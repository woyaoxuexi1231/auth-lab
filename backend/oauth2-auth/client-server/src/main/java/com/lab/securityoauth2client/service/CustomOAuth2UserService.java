package com.lab.securityoauth2client.service;

import com.lab.securityoauth2client.dto.PendingOAuthInfo;
import com.lab.securityoauth2client.entity.LocalUser;
import com.lab.securityoauth2client.entity.UserOauthBinding;
import com.lab.securityoauth2client.exception.OAuth2BindingRequiredException;
import com.lab.securityoauth2client.mapper.LocalUserMapper;
import com.lab.securityoauth2client.mapper.UserOauthBindingMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义 OAuth2UserService — OAuth2 登录后的绑定检查。
 *
 * <p>GitHub、Google 等标准 OAuth2 provider 走此服务。
 * 对 OAuth2 登录返回的用户信息进行"绑定检查"：
 * <ul>
 *   <li>已绑定（user_oauth_binding 表中有记录）→ 注入 local_user 信息，正常登录</li>
 *   <li>未绑定 → 暂存 OAuth 信息到 session，抛 OAuth2BindingRequiredException → 重定向到绑定页</li>
 * </ul>
 *
 * <p>属性标准化：不同 provider 返回的字段名不同（GitHub 用 login，Google 用 name），
 * {@code normalizeAttributes()} 统一映射为 sub、preferred_username、email、avatar_url。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserOauthBindingMapper bindingMapper;      // 查绑定关系
    private final LocalUserMapper localUserMapper;           // 查本地用户
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();  // 标准实现（真正调 /userinfo 的组件）

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // ① 调 provider 的 /userinfo 端点获取用户信息（委托给 Spring Security 默认实现）
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // ② 提取 + 标准化属性（统一字段名：sub / preferred_username / email / avatar_url）
        String provider = userRequest.getClientRegistration().getRegistrationId();  // 识别是哪个 provider（github/google/lab-client）
        Map<String, Object> attributes = normalizeAttributes(provider, oAuth2User.getAttributes());
        String providerUserId = (String) attributes.get("sub");              // 三方账号唯一 ID
        String username = (String) attributes.get("preferred_username");     // 三方用户名
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");

        // ③ 查绑定 — user_oauth_binding 表（provider + providerUserId 联合唯一）
        UserOauthBinding binding = bindingMapper.findByProviderAndProviderUserId(provider, providerUserId);

        if (binding != null) {
            // ★ 已绑定 → 加载 local_user，注入到 attributes，正常登录
            LocalUser localUser = localUserMapper.selectById(binding.getLocalUserId());
            if (localUser == null) {
                // 数据不一致：有绑定记录但本地用户被删 → 按认证失败处理
                log.error("绑定记录存在但 local_user 不存在: bindingId={}", binding.getId());
                throw new OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error(
                                "bound_user_missing", "Bound local user not found", null));
            }

            // 将 local_user 信息注入 attributes → 后续接口可获取（ProfileController 读取 local_user_id/local_username）
            Map<String, Object> enrichedAttrs = new LinkedHashMap<>(attributes);
            enrichedAttrs.put("local_user_id", localUser.getId());
            enrichedAttrs.put("local_username", localUser.getUsername());

            log.info("OAuth2 登录成功（已绑定）: {} via {}", localUser.getUsername(), provider);
            // 构建已认证用户：principal 指向 sub 字段，authorities 沿用 provider 返回的
            return new DefaultOAuth2User(oAuth2User.getAuthorities(), enrichedAttrs, "sub");
        }

        // ★ 未绑定 → 暂存到 session → 抛异常让 failureHandler 重定向到绑定页
        PendingOAuthInfo pending = new PendingOAuthInfo(
                provider, providerUserId, username, email, avatarUrl);
        getSession().setAttribute("PENDING_OAUTH_BINDING", pending);  // 绑定页从 session 读取三方信息
        log.info("OAuth2 账号未绑定: provider={}, user={}", provider, username);

        throw new OAuth2BindingRequiredException(provider, providerUserId, username);
    }

    /**
     * 标准化不同 provider 的属性字段名。
     *
     * <p>GitHub: login→preferred_username, id→sub
     * Google: name→preferred_username, sub→sub
     * Lab: sub→sub, preferred_username→preferred_username</p>
     */
    private Map<String, Object> normalizeAttributes(String provider, Map<String, Object> originalAttrs) {
        Map<String, Object> attrs = new LinkedHashMap<>(originalAttrs);

        // 用户唯一标识：优先 sub，其次 id
        String providerUserId = stringValue(
                originalAttrs.get("sub"), originalAttrs.get("id"));
        // 用户名：优先 preferred_username，其次 login/name/email
        String username = stringValue(
                originalAttrs.get("preferred_username"),
                originalAttrs.get("login"), originalAttrs.get("name"),
                originalAttrs.get("email"), providerUserId);
        // 头像：优先 avatar_url，其次 picture
        String avatarUrl = stringValue(
                originalAttrs.get("avatar_url"), originalAttrs.get("picture"));
        String email = stringValue(originalAttrs.get("email"));

        // 统一映射成标准字段名，后续业务只认这一套
        attrs.put("sub", providerUserId);
        attrs.put("preferred_username", username);
        attrs.put("name", stringValue(originalAttrs.get("name"), originalAttrs.get("login"), username));
        attrs.put("email", email);
        attrs.put("avatar_url", avatarUrl);
        attrs.put("auth_type", provider);  // 标记来源 provider，便于审计

        // 三方未验证的邮箱不可信：标记为 null 避免误用（生产可要求用户重绑邮箱）
        Object emailVerified = originalAttrs.get("email_verified");
        if (!Boolean.TRUE.equals(emailVerified) && !"true".equals(String.valueOf(emailVerified))) {
            log.warn("三方账号邮箱未验证，忽略邮箱: provider={}", provider);
            attrs.put("email", null);
        }

        return attrs;
    }

    /** 从候选值中取第一个非空非空白字符串 */
    private String stringValue(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) continue;
            String value = String.valueOf(candidate).trim();
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    /** 获取当前请求的 HttpSession（通过 RequestContextHolder） */
    private HttpSession getSession() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession();  // 暂存待绑定信息，供后续绑定页读取
    }
}
