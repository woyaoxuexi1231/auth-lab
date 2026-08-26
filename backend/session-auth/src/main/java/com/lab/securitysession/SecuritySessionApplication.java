package com.lab.securitysession;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Session 认证演示模块启动类。
 *
 * <p>本模块演示最经典的 Web 认证方式：
 * 用户登录 → 服务器创建 Session → 返回 JSESSIONID Cookie → 浏览器自动携带。
 * Session 数据存入 Redis（替代默认内存存储），实现服务重启不丢失和分布式共享。</p>
 *
 * <p>{@code @EnableRedisHttpSession} 显式声明 Session 存储后端为 Redis。
 * Spring Boot 3.x 自动配置已会根据 {@code spring.session.store-type=redis} 自动启用，
 * 此处显式声明是为了让学习者清楚地看到这一关键配置。</p>
 *
 * <p>端口：18082</p>
 */
@SpringBootApplication
@MapperScan("com.lab.securitysession.mapper")
@EnableRedisHttpSession  // ★ 切换 Session 存储后端为 Redis
public class SecuritySessionApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 容器：装配全部 Bean、注册 SecurityFilterChain 过滤器链
        SpringApplication.run(SecuritySessionApplication.class, args);
        System.out.println("""

                ╔══════════════════════════════════════════════════════════════╗
                ║       🔐 Session认证演示模块已启动 (端口: 18082)              ║
                ║  测试端点:                                                   ║
                ║    POST /api/session/login   - 表单登录                      ║
                ║    GET  /api/session/profile - 获取用户信息（需认证）          ║
                ║    GET  /api/session/info    - 查看Session详情                ║
                ║    GET  /api/session/public/hello - 公开接口                  ║
                ╚══════════════════════════════════════════════════════════════╝
                """);
    }
}
