# Spring Security Filter 执行顺序

## Filter 链概览

Spring Security 的核心是一个 **Filter 链**。每个 HTTP 请求都会依次经过这些 Filter。

```
HTTP 请求
  │
  ▼
┌─────────────────────────────────────────────┐
│  1. DisableEncodeUrlFilter                  │  防止 URL 编码问题
├─────────────────────────────────────────────┤
│  2. WebAsyncManagerIntegrationFilter        │  集成 Spring WebAsync
├─────────────────────────────────────────────┤
│  3. SecurityContextHolderFilter             │  请求开始：从 Session 恢复 SecurityContext
├─────────────────────────────────────────────┤
│  4. HeaderWriterFilter                      │  添加安全响应头
├─────────────────────────────────────────────┤
│  5. CorsFilter                              │  处理跨域请求
├─────────────────────────────────────────────┤
│  6. CsrfFilter                              │  验证 CSRF Token
├─────────────────────────────────────────────┤
│  7. LogoutFilter                            │  处理 /logout 请求
├─────────────────────────────────────────────┤
│  8. UsernamePasswordAuthenticationFilter    │  处理 POST /login（formLogin）
├─────────────────────────────────────────────┤
│  9. BasicAuthenticationFilter               │  处理 HTTP Basic 认证
├─────────────────────────────────────────────┤
│ 10. RequestCacheAwareFilter                 │  恢复被中断的请求
├─────────────────────────────────────────────┤
│ 11. SecurityContextHolderAwareRequestFilter  │  包装 HttpServletRequest
├─────────────────────────────────────────────┤
│ 12. AnonymousAuthenticationFilter           │  未登录用户给一个匿名身份
├─────────────────────────────────────────────┤
│ 13. ExceptionTranslationFilter              │  将异常转为 HTTP 响应
├─────────────────────────────────────────────┤
│ 14. AuthorizationFilter                     │  检查权限（URL → 需要的角色）
├─────────────────────────────────────────────┤
│ 15. [自定义 Filter，如 JwtAuthFilter]       │  ← 可以插在这里
└─────────────────────────────────────────────┘
  │
  ▼
DispatcherServlet → Controller
  │
  ▼
HTTP 响应
```

## 各认证方式对应的关键 Filter

### Basic 认证

```
请求 → ... → BasicAuthenticationFilter → AuthorizationFilter → Controller
              ↑
              Base64 解码
              AuthenticationManager
```

### Session 认证（登录）

```
POST /login → ... → CsrfFilter → UsernamePasswordAuthenticationFilter → ... → Controller
                              ↑
                    验证 CSRF Token      ↑
                                 提取 username/password
                                 AuthenticationManager
                                 SecurityContext 存入 Session
```

### Session 认证（后续请求）

```
GET /api/profile → ... → SecurityContextHolderFilter → ... → AuthorizationFilter → Controller
                           ↑
                    从 Session 恢复 SecurityContext
                    设置到 SecurityContextHolder
```

### JWT 认证

```
请求 → ... → JwtAuthenticationFilter → AuthorizationFilter → Controller
               ↑ (自定义 Filter，插在 UsernamePasswordAuthenticationFilter 之前)
               提取 Bearer Token
               解析 JWT
               恢复 Authentication 到 SecurityContextHolder
```

### OAuth2 客户端

```
/oauth2/authorization/lab-client
  → OAuth2AuthorizationRequestRedirectFilter → 重定向到 Auth Server

/login/oauth2/code/lab-client?code=xxx
  → OAuth2LoginAuthenticationFilter → 用 code 换 token → OAuth2AuthenticationToken
```

## 自定义 Filter 的插入位置

```java
// 方式1：在指定 Filter 之前插入
http.addFilterBefore(new JwtAuthenticationFilter(), 
    UsernamePasswordAuthenticationFilter.class);

// 方式2：在指定 Filter 之后插入
http.addFilterAfter(new CustomFilter(), 
    CsrfFilter.class);

// 方式3：在指定 Filter 的位置替换
http.addFilterAt(new CustomFilter(), 
    UsernamePasswordAuthenticationFilter.class);
```

## Filter 链中的异常处理

```
ExceptionTranslationFilter 是关键！
  ↓
AuthenticationException（认证失败）
  → AuthenticationEntryPoint 处理 → 返回 401 或重定向到登录页
  ↓
AccessDeniedException（权限不足）
  → AccessDeniedHandler 处理 → 返回 403
```
