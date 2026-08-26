package com.lab.securityoauth2resourceserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2 资源服务器启动类。
 *
 * <p>本模块演示 Spring Security OAuth2 Resource Server 的自动 JWT 验签机制：
 * 只需要配置 issuer-uri，NimbusJwtDecoder 自动从 /oauth2/jwks 获取公钥验证签名。
 * 无需自定义过滤器、无需管理密钥。</p>
 *
 * <p>端口：18086</p>
 */
@SpringBootApplication
public class SecurityOauth2ResourceServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityOauth2ResourceServerApplication.class, args);
        System.out.println("============================================");
        System.out.println("  Security OAuth2 Resource Server 启动成功！端口: 18086");
        System.out.println("============================================");
    }
}
