package com.lab.securityoauth2client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 暂存在 HTTP session 中的待绑定 OAuth 信息。
 *
 * <p>OAuth2 登录成功但未绑定时，CustomOAuth2UserService / CustomOidcUserService
 * 将 OAuth 用户信息存入 session，然后抛 OAuth2BindingRequiredException。
 * 前端重定向到 /bind 页面后，从此 session 属性读取显示。</p>
 *
 * <p>实现 {@code Serializable} — JDK 序列化兼容（Spring Session 默认使用 JDK 序列化存入 Redis）。</p>
 *
 * <p>带创建时间戳：读取处须先调用 {@link #isExpired()} 校验时效（10 分钟），超时按"无暂存"处理。</p>
 */
@Data
@NoArgsConstructor
public class PendingOAuthInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String provider;            // 三方提供方（github/google/lab-client）
    private String providerUserId;      // Provider 侧的用户唯一标识（sub）
    private String providerUsername;    // Provider 侧的用户显示名
    private String email;               // Provider 侧邮箱
    private String avatarUrl;           // Provider 侧头像

    /** 创建时间戳（毫秒）— 用于判断暂存信息是否超时失效 */
    private long createdAtMillis = System.currentTimeMillis();

    public PendingOAuthInfo(String provider, String providerUserId, String providerUsername,
                            String email, String avatarUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerUsername = providerUsername;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.createdAtMillis = System.currentTimeMillis();
    }

    /** 暂存信息是否在有效期内（10 分钟），超时视为无效 */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAtMillis > 10 * 60 * 1000L;
    }
}
