package com.lab.securityoauth2authserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2 认证服务器启动类。
 *
 * <p>使用 Spring Authorization Server 实现标准 OAuth2 Authorization Code 授权码模式。
 * 本模块扮演 OAuth2 协议中的"认证服务器"角色，负责用户登录、授权确认、Token 签发。</p>
 *
 * <p>端口：18085</p>
 */
@SpringBootApplication
@MapperScan("com.lab.securityoauth2authserver.mapper")
public class SecurityOauth2AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityOauth2AuthServerApplication.class, args);
        System.out.println("============================================================");
        System.out.println("  OAuth2认证服务器启动成功！端口: 18085");
        System.out.println("  OAuth2端点: /api/oauth2-auth/authorize, /api/oauth2-auth/token");
        System.out.println("============================================================");
    }
}
