# Authentication 生命周期

## 什么是 Authentication

`Authentication` 是 Spring Security 中表示"当前用户是谁"的核心接口。
它包含三个关键信息：

| 方法                | 含义       | 示例                       |
|-------------------|----------|--------------------------|
| getPrincipal()    | 用户身份（谁）  | "admin" / UserDetails 对象 |
| getCredentials()  | 凭证（凭什么）  | "123456" → 认证后设为 null    |
| getAuthorities()  | 权限（能做什么） | [ROLE_USER, ROLE_ADMIN]  |
| isAuthenticated() | 是否已认证    | true / false             |
| getDetails()      | 额外信息     | IP、Session ID 等          |

## Authentication 的一生

```
┌──────────────────────────────────────────────────────┐
│                   未认证阶段                           │
│                                                      │
│  ① 创建未认证的 Authentication                       │
│     new UsernamePasswordAuthenticationToken(          │
│       username, password                               │
│     )                                                 │
│     isAuthenticated() = false                         │
│     authorities = 空                                  │
│                                                      │
├──────────────────────────────────────────────────────┤
│                   认证阶段                            │
│                                                      │
│  ② AuthenticationManager.authenticate(unAuthToken)   │
│     ↓                                                │
│  ③ 遍历 AuthenticationProvider 列表                  │
│     - RememberMeAuthenticationProvider               │
│     - DaoAuthenticationProvider ← 匹配！              │
│     ↓                                                │
│  ④ DaoAuthenticationProvider:                        │
│     UserDetails user = userDetailsService             │
│                         .loadUserByUsername(username) │
│     passwordEncoder.matches(rawPwd, user.password)    │
│     ↓                                                │
│  ⑤ 认证成功！返回新的 Authentication                  │
│     new UsernamePasswordAuthenticationToken(           │
│       user,          // principal                     │
│       null,          // credentials（已清除密码）       │
│       user.getAuthorities()                           │
│     )                                                 │
│     isAuthenticated() = true                          │
│                                                      │
├──────────────────────────────────────────────────────┤
│                   使用阶段                            │
│                                                      │
│  ⑥ 存入 SecurityContext                              │
│     securityContext.setAuthentication(authToken)      │
│                                                      │
│  ⑦ 存入 SecurityContextHolder（ThreadLocal）         │
│     SecurityContextHolder.setContext(securityContext) │
│                                                      │
│  ⑧ 整个请求期间可用                                  │
│     Authentication auth = SecurityContextHolder       │
│       .getContext().getAuthentication()               │
│                                                      │
│  ⑨ Controller 中使用                                 │
│     auth.getName() → "admin"                         │
│     auth.getAuthorities() → [ROLE_USER]              │
│                                                      │
├──────────────────────────────────────────────────────┤
│                   持久化阶段                           │
│                                                      │
│  ⑩ Session 认证：存入 HttpSession                     │
│     session.setAttribute(                             │
│       "SPRING_SECURITY_CONTEXT",                      │
│       securityContext                                 │
│     )                                                 │
│                                                      │
│  ⑪ JWT 认证：编码到 JWT Claims 中                     │
│     claims.put("sub", username)                       │
│     claims.put("authorities", [...] )                 │
│                                                      │
├──────────────────────────────────────────────────────┤
│                   销毁阶段                            │
│                                                      │
│  ⑫ 请求结束                                          │
│     SecurityContextHolder.clearContext()              │
│     （ThreadLocal 清除，防止内存泄漏）                 │
│                                                      │
│  ⑬ Session 登出                                      │
│     session.invalidate()                              │
│     Authentication 随 Session 一起销毁                │
│                                                      │
│  ⑭ JWT 过期                                          │
│     Token 超过 exp 时间，解析失败                      │
│     Authentication 不再能被恢复                       │
└──────────────────────────────────────────────────────┘
```

## 不同认证方式的 Authentication 类型

| 认证方式                   | Authentication 类型                   | 特点                     |
|------------------------|-------------------------------------|------------------------|
| Basic                  | UsernamePasswordAuthenticationToken | 每个请求新建                 |
| Session（登录时）           | UsernamePasswordAuthenticationToken | 登录时创建，存入 Session       |
| Session（后续请求）          | UsernamePasswordAuthenticationToken | 从 Session 恢复           |
| JWT                    | UsernamePasswordAuthenticationToken | 从 JWT Claims 重建        |
| OAuth2 Client          | OAuth2AuthenticationToken           | 包含第三方用户属性              |
| OAuth2 Resource Server | JwtAuthenticationToken              | NimbusJwtDecoder 验证后创建 |
| Anonymous              | AnonymousAuthenticationToken        | 未登录用户                  |
| Remember Me            | RememberMeAuthenticationToken       | 记住我功能                  |

## 关键设计

### 认证后清除密码

```
认证前: credentials = "123456"（明文密码）
认证后: credentials = null

为什么？
- 防止密码在内存中留存
- 减少泄露风险
```

### ThreadLocal 存储

```
SecurityContextHolder 使用 ThreadLocal 存储 SecurityContext
  → 每个线程（请求）拥有独立的 SecurityContext
  → 线程隔离，不会互相干扰
  → 请求结束必须清理！（防止内存泄漏）
```
