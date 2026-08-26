package com.lab.securityoauth2client;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OAuth2 客户端启动类。
 *
 * <p>本模块扮演 OAuth2 协议中的"客户端"角色：
 * 委托认证服务器完成用户身份验证，用授权码换取 Token，用 Token 访问资源。</p>
 *
 * <p>同时支持本地表单登录 + OAuth2 三方登录 + 账号绑定。</p>
 *
 * <p>端口：18087</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.lab.securityoauth2client.mapper")
public class SecurityOauth2ClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityOauth2ClientApplication.class, args);
        System.out.println("============================================");
        System.out.println("  Security OAuth2 Client 模块启动成功！端口: 18087");
        System.out.println("  授权入口: /api/oauth2-client/authorization/lab-client");
        System.out.println("============================================");
    }
}
