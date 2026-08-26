package com.lab.securityjwtauthserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JWT 认证服务器启动类。
 *
 * <p>本模块演示 JWT（JSON Web Token）无状态认证：
 * 服务器签发 Token → 客户端存储 → 每次请求携带 → 服务器验签恢复身份。
 * 全程不依赖 Session，适合前后端分离和微服务架构。</p>
 *
 * <p>端口：18083（Docker）/ 通过 Vite 代理访问（本地开发）</p>
 */
@SpringBootApplication
@MapperScan("com.lab.securityjwtauthserver.mapper")  // 扫描 MyBatis Plus Mapper 接口
public class SecurityJwtAuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityJwtAuthServerApplication.class, args);
        System.out.println("""
                ============================================================
                  JWT 认证服务器启动成功！端口: 18083
                  测试接口:
                    POST /api/jwt/auth/login   - 登录获取 JWT
                    GET  /api/jwt/profile      - 获取用户信息（需 JWT）
                    GET  /api/jwt/public/hello - 公开接口（无需认证）
                ============================================================
                """);
    }
}
