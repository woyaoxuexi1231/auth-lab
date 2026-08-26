package com.lab.securityoauth2client.dto;

import lombok.Data;

/**
 * 注册请求 DTO — 用户名 + 密码 + 邮箱（可选）。
 */
@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
}
