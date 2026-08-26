package com.lab.securitysession.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis Session 序列化配置说明。
 *
 * <p>这个类本身不注册任何 Bean，它的价值在于详细解释了
 * 为什么 Session 认证选择 JDK 序列化（Spring Session 默认）而非 JSON 序列化。</p>
 *
 * <p>核心原因：Spring Security 的 {@code UsernamePasswordAuthenticationToken}
 * 没有默认构造函数，Jackson JSON 反序列化会丢失 principal 和 authorities，
 * 导致从 Redis 恢复的 SecurityContext 中 getName() 返回空字符串、getAuthorities() 返回空列表。</p>
 *
 * <p>JDK 序列化的优缺点：
 * <ul>
 *   <li>✅ Spring Security 所有类都实现了 Serializable，完全兼容</li>
 *   <li>✅ 开箱即用，不需要额外配置</li>
 *   <li>❌ redis-cli 中不可读（二进制格式）</li>
 *   <li>❌ 仅 Java 可用（跨语言受限）</li>
 * </ul>
 *
 * <p>如果需要 JSON 序列化（例如用其他语言读 Redis），需要编写自定义 Mixin。
 * 这不是官方推荐的做法，详见类注释中保留的示例代码。</p>
 */
@Configuration
public class RedisConfig {
    // 无需任何 Bean — Spring Session 默认使用 JdkSerializationRedisSerializer
}
