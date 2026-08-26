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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 自定义 OIDC 用户服务 — 与 CustomOAuth2UserService 逻辑相同，但处理 OIDC 流程。
 *
 * <p>OIDC（OpenID Connect）与标准 OAuth2 的区别：
 * OIDC 有 id_token（JWT 格式），用户信息直接包含在其中，不需要额外调 /userinfo。
 * lab-client、Google 等 OIDC provider 走此服务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserOauthBindingMapper bindingMapper;
    private final LocalUserMapper localUserMapper;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // ① 从 id_token 获取用户信息（不需额外调 /userinfo）
        // id_token 是 JWT：签名已在框架层校验，claims 可信
        OidcIdToken idToken = userRequest.getIdToken();
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> claims = normalizeClaims(provider, idToken.getClaims());  // 标准化字段名

        String providerUserId = (String) claims.get("sub");               // 三方用户唯一 ID
        String username = (String) claims.get("preferred_username");
        String email = (String) claims.get("email");
        String avatarUrl = (String) claims.get("avatar_url");

        // ② 查绑定（与 CustomOAuth2UserService 完全同逻辑）
        UserOauthBinding binding = bindingMapper.findByProviderAndProviderUserId(provider, providerUserId);

        if (binding != null) {
            // ★ 已绑定 → 加载 local_user 并注入 claims
            LocalUser localUser = localUserMapper.selectById(binding.getLocalUserId());
            if (localUser == null) {
                throw new OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error(
                                "bound_user_missing", "Bound local user not found", null));
            }

            Map<String, Object> enrichedClaims = new LinkedHashMap<>(claims);
            enrichedClaims.put("local_user_id", localUser.getId());      // 供 ProfileController 识别本地用户
            enrichedClaims.put("local_username", localUser.getUsername());

            // 构建 OIDC authorities（OidcUserAuthority + scope authorities）
            // OIDC 规范：用户角色来自 id_token，scope 权限也要作为 SCOPE_xxx 权限加入
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            authorities.add(new OidcUserAuthority(idToken));
            userRequest.getAccessToken().getScopes()
                    .forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));

            log.info("OIDC 登录成功（已绑定）: {} via {}", localUser.getUsername(), provider);
            // 构建 OIDC 用户：携带 id_token + 增强后的 claims（sub 为主键）
            return new DefaultOidcUser(authorities, idToken, new OidcUserInfo(enrichedClaims), "sub");
        }

        // ★ 未绑定 → 暂存 session → 抛异常
        PendingOAuthInfo pending = new PendingOAuthInfo(
                provider, providerUserId, username, email, avatarUrl);
        getSession().setAttribute("PENDING_OAUTH_BINDING", pending);

        throw new OAuth2BindingRequiredException(provider, providerUserId, username);
    }

    /** 标准化不同 OIDC provider 的 claims */
    private Map<String, Object> normalizeClaims(String provider, Map<String, Object> originalClaims) {
        Map<String, Object> claims = new LinkedHashMap<>(originalClaims);

        String providerUserId = stringValue(originalClaims.get("sub"));
        String username = stringValue(
                originalClaims.get("preferred_username"),
                originalClaims.get("name"), providerUserId);
        String email = stringValue(originalClaims.get("email"));
        String avatarUrl = stringValue(
                originalClaims.get("avatar_url"), originalClaims.get("picture"));

        // 统一映射为业务可依赖的标准字段
        claims.put("sub", providerUserId);
        claims.put("preferred_username", username);
        claims.put("name", stringValue(originalClaims.get("name"), username));
        claims.put("email", email);
        claims.put("avatar_url", avatarUrl);
        claims.put("auth_type", provider);

        // 三方未验证的邮箱不可信：标记为 null 避免误用（生产可要求用户重绑邮箱）
        Object emailVerified = originalClaims.get("email_verified");
        if (!Boolean.TRUE.equals(emailVerified) && !"true".equals(String.valueOf(emailVerified))) {
            log.warn("三方账号邮箱未验证，忽略邮箱: provider={}", provider);
            claims.put("email", null);
        }

        return claims;
    }

    private String stringValue(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) continue;
            String value = String.valueOf(candidate).trim();
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    private HttpSession getSession() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession();
    }
}
