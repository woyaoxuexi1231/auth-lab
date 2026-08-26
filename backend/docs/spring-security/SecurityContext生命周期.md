# SecurityContext 生命周期

## 什么是 SecurityContext

`SecurityContext` 是 Spring Security 中存储当前安全上下文（当前用户是谁、有什么权限）的容器。
它与 `SecurityContextHolder` 配合使用，使应用的任何地方都能获取当前用户信息。

## 核心关系

```
SecurityContextHolder (持有者，使用 ThreadLocal)
  │
  └── SecurityContext (上下文容器)
        │
        └── Authentication (认证信息)
              ├── principal (用户身份)
              ├── authorities (权限)
              └── authenticated (是否已认证)
```

## 生命周期图

```
┌─────────────────────────────────────────────────────────┐
│                   请求开始                                │
│                                                         │
│  ① SecurityContextHolderFilter (或 PersistenceFilter)   │
│     ┌─────────────────────────────────────────────┐     │
│     │ Session 认证：                                │     │
│     │   HttpSession session = request.getSession() │     │
│     │   SecurityContext ctx = session.getAttribute(│     │
│     │     "SPRING_SECURITY_CONTEXT"                 │     │
│     │   )                                           │     │
│     │                                              │     │
│     │ JWT 认证：                                    │     │
│     │   解析 JWT → 创建新的 SecurityContext         │     │
│     │                                              │     │
│     │ Basic 认证：                                  │     │
│     │   BasicAuthenticationFilter                   │     │
│     │   → 认证 → 创建新的 SecurityContext          │     │
│     └─────────────────────────────────────────────┘     │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                   请求处理中                              │
│                                                         │
│  ② SecurityContextHolder.setContext(securityContext)    │
│     ↓                                                   │
│  ③ 应用代码可以获取：                                    │
│     Authentication auth = SecurityContextHolder         │
│       .getContext().getAuthentication()                 │
│     ↓                                                   │
│  ④ Controller / Service 使用当前用户信息                │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                   请求结束                                │
│                                                         │
│  ⑤ Session 认证：                                       │
│     SecurityContextPersistenceFilter                     │
│     session.setAttribute(                                │
│       "SPRING_SECURITY_CONTEXT",                         │
│       securityContext                                    │
│     )                                                    │
│     ← SecurityContext 持久化到 Session                   │
│                                                         │
│  ⑥ SecurityContextHolder.clearContext()                 │
│     ← 清除 ThreadLocal，防止内存泄漏                    │
│     ← SecurityContext 对象等待 GC                       │
│                                                         │
│  ⑦ JWT / Basic 认证：                                   │
│     没有持久化步骤（无状态）                              │
│     SecurityContext 随请求结束而消失                     │
│     下次请求：重新创建                                   │
└─────────────────────────────────────────────────────────┘
```

## 不同认证方式的 SecurityContext 生命周期

### Session 认证

```
请求1（登录）:
  SecurityContext 创建 → 认证 → 存入 HttpSession → 存入 Redis

请求2（访问API）:
  从 HttpSession/Redis 恢复 SecurityContext → 使用 → 请求结束（仍在 Session 中）

请求3（登出）:
  从 HttpSession 获取 SecurityContext → Session.invalidate()
  → SecurityContext 销毁 → Redis 中删除
```

**特点**：SecurityContext 跨越多个请求，在 Session 有效期内持续存在。

### JWT 认证

```
请求1（登录）:
  认证 → 生成 JWT（包含用户信息）→ 返回给客户端
  SecurityContext 请求结束后销毁

请求2（访问API）:
  解析 JWT → 创建新的 SecurityContext → 使用 → 请求结束销毁

请求3（访问API）:
  解析 JWT → 创建新的 SecurityContext → 使用 → 请求结束销毁
```

**特点**：每个请求都创建新的 SecurityContext，请求结束即销毁。完全无状态。

### Basic 认证

```
请求1:
  Base64 解码 → 认证 → 创建 SecurityContext → 使用 → 请求结束销毁

请求2:
  Base64 解码 → 认证 → 创建 SecurityContext → 使用 → 请求结束销毁
```

**特点**：与 JWT 类似，每个请求新建 SecurityContext。但区别是 Basic 每次都要查数据库。

## SecurityContextHolder 的策略

```java
// 默认策略：MODE_THREADLOCAL
// SecurityContext 存在 ThreadLocal 中
SecurityContextHolder.setStrategyName(
    SecurityContextHolder.MODE_THREADLOCAL
);

// 全局策略：MODE_GLOBAL
// 所有线程共享（不推荐）

// 可继承策略：MODE_INHERITABLETHREADLOCAL
// 子线程可以继承父线程的 SecurityContext
```

## 关键注意事项

1. **请求结束必须清理**：`SecurityContextHolder.clearContext()` 在 Filter 链末尾自动执行
2. **异步方法**：使用 `@Async` 时，子线程不会自动继承 SecurityContext（需要配置 MODE_INHERITABLETHREADLOCAL 或手动传递）
3. **不要在 SecurityContext 中存大量数据**：Session 持久化场景下，SecurityContext 会序列化到 Redis
4. **不要直接修改 Authentication**：通过 SecurityContext.setAuthentication() 来设置新的认证信息
