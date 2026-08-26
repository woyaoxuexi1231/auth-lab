package com.lab.securityopaquetoken;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Opaque Token 认证服务启动类。
 *
 * <p>本模块演示"不透明令牌"认证：Token 本身是随机 UUID，
 * 不携带任何用户信息，所有信息存储在 Redis 中。</p>
 *
 * <p>与 JWT 的核心区别：
 * <ul>
 *   <li>JWT — Token 自带用户信息，本地验签，无法撤销</li>
 *   <li>Opaque Token — Token 只是 Redis Key，查 Redis 验证，可随时撤销</li>
 * </ul>
 *
 * <p>端口：18088</p>
 */
@SpringBootApplication
public class SecurityOpaqueTokenApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityOpaqueTokenApplication.class, args);  // 启动容器：注册过滤器链与全部 Bean
    }
}
