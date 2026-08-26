package com.lab.securityjwtauthserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 响应 DTO — 登录成功后返回给客户端。
 *
 * <p>本模块采用单 Token 方案（不引入 OAuth2 的 Refresh Token 概念）：
 * Token 过期后用户重新登录即可。</p>
 *
 * <p>响应示例：
 * <pre>{@code
 * {
 *   "token": "eyJhbGciOiJIUzI1NiJ9...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 3600000
 * }
 * }</pre>
 *
 * <p>客户端使用方式：
 * <pre>{@code
 * fetch('/api/jwt/profile', {
 *   headers: { 'Authorization': tokenType + ' ' + token }
 * })
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private String token;       // JWT 字符串（Header.Payload.Signature）
    private String tokenType;   // 固定 "Bearer" — 请求头格式：Authorization: Bearer <token>
    private Long expiresIn;     // 过期时间（毫秒），3600000 = 1 小时
}
