package com.lab.securityoauth2authserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2 客户端实体 — 映射 security_lab_oauth2.oauth2_registered_client 表。
 *
 * <p>客户端注册数据不再硬编码在内存里，而是持久化到数据库。
 * 多值字段（认证方式 / 授权类型 / 回调地址 / scope）用逗号分隔存储，
 * 由 {@link com.lab.securityoauth2authserver.service.DbRegisteredClientRepository} 负责解析和组装。</p>
 */
@Data
@TableName("oauth2_registered_client")
public class OAuth2Client {

    @TableId(type = IdType.INPUT)
    private String id;  // RegisteredClient.id（UUID，与 client_id 解耦；编辑时保持不变）

    @TableField("client_id")
    private String clientId;  // 业务客户端 ID（/authorize、/token 请求中提交）

    @TableField("client_id_issued_at")
    private LocalDateTime clientIdIssuedAt;  // 签发时间

    @TableField("client_secret")
    private String clientSecret;  // BCrypt 密文（{bcrypt} 前缀）

    @TableField("client_name")
    private String clientName;  // 展示名称（授权确认页显示）

    @TableField("client_authentication_methods")
    private String clientAuthenticationMethods;  // 逗号分隔：client_secret_basic/client_secret_post/none

    @TableField("authorization_grant_types")
    private String authorizationGrantTypes;  // 逗号分隔：authorization_code/refresh_token/client_credentials

    @TableField("redirect_uris")
    private String redirectUris;  // 逗号分隔的回调地址白名单

    @TableField("post_logout_redirect_uris")
    private String postLogoutRedirectUris;  // 登出后允许跳转的地址

    private String scopes;  // 逗号分隔的 scope 白名单

    @TableField("require_proof_key")
    private Boolean requireProofKey;  // 是否强制 PKCE

    @TableField("require_authorization_consent")
    private Boolean requireAuthorizationConsent;  // 是否要求用户授权确认

    @TableField("access_token_ttl_seconds")
    private Integer accessTokenTtlSeconds;  // Access Token 有效期（秒）

    @TableField("refresh_token_ttl_seconds")
    private Integer refreshTokenTtlSeconds;  // Refresh Token 有效期（秒）

    @TableField("reuse_refresh_tokens")
    private Boolean reuseRefreshTokens;  // 刷新令牌是否可复用

    @TableField("create_time")
    private LocalDateTime createTime;  // 创建时间

    @TableField("update_time")
    private LocalDateTime updateTime;  // 更新时间
}