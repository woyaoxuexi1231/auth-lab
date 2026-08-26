package com.lab.securityoauth2resourceserver.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息 DTO — 封装从 JWT Token 提取的 claims。
 */
@Data
@Builder
public class UserProfile {
    private String subject;             // JWT sub claim
    private String issuer;              // JWT iss claim
    private LocalDateTime issuedAt;     // JWT iat claim
    private LocalDateTime expiresAt;    // JWT exp claim
    private List<String> audience;      // JWT aud claim
    private String jwtId;               // JWT jti claim（唯一标识）
    private List<String> authorities;   // 从 scope claim 提取的权限
}
