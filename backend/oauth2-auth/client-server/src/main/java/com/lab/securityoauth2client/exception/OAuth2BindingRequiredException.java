package com.lab.securityoauth2client.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * OAuth2 绑定检查异常 — OAuth2 登录成功但三方账号未绑定本地账户时抛出。
 *
 * <p>SecurityConfig 的 failureHandler 检测到此异常后，
 * 将用户重定向到 /#/bind 页面进行绑定操作。</p>
 */
public class OAuth2BindingRequiredException extends OAuth2AuthenticationException {

    private static final OAuth2Error BINDING_REQUIRED =
            new OAuth2Error("binding_required", "OAuth account not bound to a local user", null);

    private final String provider;           // e.g., "github", "lab-client"
    private final String providerUserId;     // Provider 的用户唯一标识（sub）
    private final String providerUsername;   // Provider 的用户名

    public OAuth2BindingRequiredException(String provider, String providerUserId, String providerUsername) {
        super(BINDING_REQUIRED, BINDING_REQUIRED.toString());
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerUsername = providerUsername;
    }

    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getProviderUsername() { return providerUsername; }
}
