package com.lab.securityjwtauthserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求 DTO。
 *
 * <p>Jackson 自动将 JSON 请求体反序列化为此对象。
 * 只包含 username 和 password 两个字段，
 * 不会暴露 Entity 中的内部字段（如 id、createTime）。</p>
 *
 * <p>请求示例：
 * <pre>{@code
 * POST /api/jwt/auth/login
 * Content-Type: application/json
 * { "username": "admin", "password": "123456" }
 * }</pre>
 */
@Data                           // getter/setter/toString/equals/hashCode
@Builder                        // LoginRequest.builder().username("admin").build()
@NoArgsConstructor              // Jackson 反序列化需要无参构造
@AllArgsConstructor             // 全参构造
public class LoginRequest {
    private String username;
    private String password;    // 明文密码 — 只在内存中短暂存在，认证成功后清除
}
