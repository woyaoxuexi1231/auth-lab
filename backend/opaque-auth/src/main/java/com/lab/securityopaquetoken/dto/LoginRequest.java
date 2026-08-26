package com.lab.securityopaquetoken.dto;

import lombok.Data;

/** 登录请求 DTO — 替代 Map 接收，字段名拼写错误可被编译期发现 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
