package com.lab.securityoauth2authserver.dto;

import lombok.Data;

/**
 * 客户端注册表单。
 *
 * <p>注意：布尔字段统一默认 false，模板里用 HTML checked 属性实现"默认勾选"，
 * 用户取消勾选后请求里没有该参数 → Spring 保留 false，避免"取消无效"的坑。</p>
 */
@Data
public class ClientCreateForm {

    /** 客户端名称 */
    private String clientName;

    /** 客户端 ID（唯一） */
    private String clientId;

    /** 客户端密钥（明文，服务端 BCrypt 加密后入库） */
    private String clientSecret;

    /** 回调地址（支持换行/逗号分隔多个） */
    private String redirectUris;

    /** 授权范围（逗号分隔，如 openid,profile,read） */
    private String scopes;

    /** 客户端认证方式（client_secret_basic / client_secret_post / none） */
    private String[] authMethods;

    /** 授权类型（authorization_code / refresh_token / client_credentials ...） */
    private String[] grantTypes;

    private boolean requireProofKey;
    private boolean requireAuthorizationConsent;
    private boolean reuseRefreshTokens;

    /** 访问令牌有效期（秒），空则默认 3600 */
    private Integer accessTokenTtlSeconds;

    /** 刷新令牌有效期（秒），空则默认 604800 */
    private Integer refreshTokenTtlSeconds;
}