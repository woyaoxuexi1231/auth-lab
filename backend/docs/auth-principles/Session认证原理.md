# Session 认证原理

> **阅读建议**：第 1~3 章讲的是 Session 认证**机制本身**——不依赖任何框架，
> 弄懂这三章，你就明白了 Session 认证的本质；
> 第 4 章才是"Spring Security 是如何完成这套操作的"，结合本项目的
> `session-auth` 模块看效果更佳。

---

## 1. 机制本质：为什么需要 Session

HTTP 协议是**无状态**的：服务器收到每一个请求，都像第一次见面——它不知道
"这次请求的人"和"上次请求的人"是不是同一个人。但几乎每个业务都需要跨请求
记住用户（购物车、登录状态……），于是有了 Session 认证：

```
核心思想：把"登录状态"存在服务器上，客户端只保留一张"取件小票"。
```

类比一个**储物柜**：

| 类比对象   | 储物柜场景                     | Session 认证场景                 |
|---------|----------------------------|-----------------------------|
| 柜子     | 储物柜（存放行李）                 | 服务器（存放登录状态）                 |
| 行李     | 你的物品                       | 会话数据（用户是谁、有什么权限、登录时间…）     |
| 小票     | 印着柜号的纸质凭条                 | Session ID（一串随机字符串）          |
| 取件     | 出示小票 → 管理员按号开柜            | 请求携带 Session ID → 服务器查会话恢复身份 |

**关键认知**：小票本身不含任何行李信息（它不是"浓缩的行李"），它只是**一把钥匙**。
同理，Session ID 本身没有任何用户信息，它只是**服务器端会话记录的索引**。
所有状态都在服务器上，这是理解 Session 认证最重要的一点。

---

## 2. 完整认证流程

整个生命周期分三个阶段：**登录（建立会话）→ 携带访问（恢复身份）→ 登出/过期（销毁会话）**。

参与方只有三个：**客户端**（浏览器）、**服务器**（应用 + 会话存储）。

### 2.1 登录：建立会话

```
① 客户端 POST /login，提交凭证 {username, password}
      ↓
② 服务器验证凭证（比对数据库中的用户与密码哈希）
      ↓
③ 验证通过 → 服务器创建一条会话记录：
   - 生成一个随机 Session ID（足够随机，不可预测）
   - 把用户身份、权限等状态写入会话存储（内存 / Redis / 数据库）
   - 设置有效期（TTL）
      ↓
④ 服务器返回响应：
   Set-Cookie: SESSIONID=<随机串>; HttpOnly; SameSite=Lax
      ↓
⑤ 浏览器收到 Set-Cookie 后自动保存在 Cookie Store
      ↓
⑥ 登录完成 —— 服务器认得了这个客户端
```

注意第④步：服务器**没有把任何用户信息放进 Cookie**，放进 Cookie 的只是一个
服务器自己能查到的 ID。身份状态在哪？在服务器（第③步的会话存储里）。

### 2.2 后续请求：恢复身份

```
① 浏览器对同域名的每个请求，自动携带 Cookie: SESSIONID=<随机串>
      ↓
② 服务器从请求中取出 Session ID
      ↓
③ 用 Session ID 到会话存储中查找会话记录
      ↓
④ 找到 → 服务器恢复"这个请求是谁" → 正常处理业务
   找不到（已过期/已被删除）→ 视为未登录 → 拒绝访问
```

整个过程中**客户端零操作**——携带 Cookie 是浏览器的自动行为，
这也是 Session 认证"对客户端透明"的由来。

### 2.3 登出与过期：销毁会话

```
登出（显式销毁）：
① 客户端 POST /logout
      ↓
② 服务器删除会话记录 → 该 Session ID 立即失效
      ↓
③ 响应 Set-Cookie: SESSIONID=; Max-Age=0 → 浏览器删除本地 Cookie

过期（被动销毁）：
服务器端的会话记录有 TTL，到期自动删除；
之后客户端再带这个 Session ID 来，服务器查不到 → 自动视为未登录
```

> 所以"登出"和"过期"的本质都是**删除服务器端那一条会话记录**，
> 而不是"修改客户端的东西"。客户端那张小票，本身毫无价值。

### 2.4 一张图看全流程

```
客户端                          服务器                          会话存储
  │                              │                               │
  │  POST /login                 │                               │
  │  {username, password}        │                               │
  │ ────────────────────────────▶│                               │
  │                              │ ① 验证凭证                     │
  │                              │ ② 生成 Session ID              │
  │                              │ ③ 保存会话记录 ────────────────▶│
  │                              │        (用户信息+TTL)           │
  │  Set-Cookie: SESSIONID=xxx   │                               │
  │ ◀────────────────────────────│                               │
  │                              │                               │
  │  GET /api/profile            │                               │
  │  Cookie: SESSIONID=xxx       │                               │
  │ ────────────────────────────▶│                               │
  │                              │ ④ 按 ID 查会话 ───────────────▶│
  │                              │ ◀────── 会话记录(找到) ────────│
  │                              │ ⑤ 身份恢复，处理请求             │
  │  200 OK {username: "admin"}  │                               │
  │ ◀────────────────────────────│                               │
  │                              │                               │
  │  POST /logout                │                               │
  │  Cookie: SESSIONID=xxx       │                               │
  │ ────────────────────────────▶│                               │
  │                              │ ⑥ 删除会话记录 ───────────────▶│
  │  Set-Cookie: SESSIONID=; Max-Age=0                           │
  │ ◀────────────────────────────│                               │
```

---

## 3. 设计要点

### 3.1 Session ID 是一张"不透明票据"

Session ID 本身**不含任何信息**，只是随机字符串。它和 Opaque Token
（见 [OpaqueToken认证原理](OpaqueToken认证原理.md)）是**同构**的：

|             | Session ID      | Opaque Token        |
|-------------|-----------------|---------------------|
| 凭据本身携带信息？ | 否（纯随机字符串）      | 否（纯随机字符串）          |
| 状态存在哪      | 服务器的会话存储       | 服务器的 Token 存储      |
| 服务器如何验证    | 查会话存储           | 查 Token 存储         |
| 能否主动失效     | 能（删会话）          | 能（删 Token）         |

区别只在于**存储的内容和粒度**：会话里通常是一个完整的"用户会话上下文"，
Opaque Token 里通常是一条精简的"Token 信息"。对比之下，**JWT 是携带信息、
本地验签**的，三者本质差异就在"状态在服务器还是凭据里"。

### 3.2 会话状态存在哪

| 存储位置  | 优点                        | 缺点                    | 适用场景          |
|-------|---------------------------|-----------------------|---------------|
| 应用内存  | 最快、零依赖                  | 重启丢失、多实例不共享、无法扩容       | 单机、临时开发       |
| Redis | 重启不丢失、多实例共享、独立过期、可监控   | 多一次网络往返、需要运维 Redis     | 生产常规选择（本项目）   |
| 数据库   | 持久化、可查询、可做分析           | 慢、会话读写频繁易成瓶颈            | 需要审计/统计的特殊场景 |

### 3.3 过期策略

| 策略       | 含义                          | 例子               |
|----------|-----------------------------|------------------|
| 固定过期     | 会话从创建起 N 分钟后必然失效，无论是否活跃   | 银行 App 登录 5 分钟超时 |
| 滑动过期     | 每次有请求就重置 TTL，只要一直活跃就不过期    | 大多数 Web 站点（默认）  |

### 3.4 并发会话控制

同一账号能否同时在多台设备登录？常见的两种策略：

- **允许并发**：每台设备一个独立会话（默认行为）
- **单点登录**：同一账号只允许一个有效会话，新登录踢掉旧会话
  （本项目演示了这种策略，见 4.6）

### 3.5 安全攻击面

| 攻击               | 原理                                     | 防护                                   |
|------------------|----------------------------------------|--------------------------------------|
| Session 固定攻击    | 攻击者先拿一个合法 Session ID 塞给受害者，受害者登录后 ID 不变 → 共会话 | 登录成功后**更换新的 Session ID**             |
| Session 劫持       | 窃取 Cookie（网络嗅探 / 恶意脚本 / 浏览器漏洞）             | HTTPS + HttpOnly + Secure Cookie + 定期轮换 ID |
| XSS             | 恶意脚本读 Cookie 传给攻击者                          | `HttpOnly`：Cookie 对 JS 不可见              |
| CSRF            | 跨站请求自动携带 Cookie，攻击者诱导用户发起请求               | SameSite Cookie + CSRF Token             |

> 注意 CSRF 的根源：Cookie 由浏览器**自动携带**，所以跨站请求也会带上。
> 这是 Session 认证特有的攻击面——JWT 放在 Authorization 头（JS 手动添加），
> 天然没有这个问题，这也是 JWT 流行起来的原因之一。

### 3.6 优缺点总结

**优点：**

- 状态在服务端，**可以随时踢人**（删会话即下线），可控性强
- 客户端只需存一个短 ID，**没有敏感信息暴露**
- 权限、用户信息变更**即时生效**（下次请求查到的就是最新状态）
- 对客户端透明（Cookie 自动携带），浏览器场景几乎零开发量
- 框架支持成熟（Servlet 容器、Spring Session 都是原生能力）

**缺点：**

- **有状态**：服务器必须存储会话 → 多实例部署要共享存储（Redis），否则用户被随机踢下线
- 扩容/重启要处理会话迁移
- 浏览器之外（App、小程序、服务间调用）需要自己管理 Cookie，不天然适用
- 依赖 Cookie → 跨域、CSRF 问题随之而来

**适用场景**：传统 Web 应用、需要强管控（可踢人、可审计）的 B 端系统；
**不适用**：纯 API 服务（无浏览器）、跨域多端、微服务间认证。

---

## 4. Spring Security 如何实现 Session 认证

> 本章开始出现框架概念。**机制是"什么"，框架是"怎么用代码完成"**。
> 先看架构图建立整体认知，再顺着流程走一遍。
> （组件/配置的具体说明都写在 `session-auth` 模块的代码注释里，
> 打开 `SecurityConfig.java` 即可看到）

### 4.1 架构图：组件全景

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     session-auth 单服务（Port 18082）                      │
│                                                                         │
│   浏览器 ──HTTP──▶ Tomcat（Servlet 容器）                                  │
│                      │                                                   │
│                      ▼                                                   │
│       Spring Security 过滤器链 —— 每个请求都依次穿过                          │
│   ┌───────────────────────────────────────────────────────────────┐     │
│   │ SecurityContextHolderFilter        ① 请求开始：从 Session 恢复身份│     │
│   │   └── 读 HttpSession → 取 SPRING_SECURITY_CONTEXT → ThreadLocal │     │
│   │ UsernamePasswordAuthenticationFilter ② 登录：解析凭证并认证        │     │
│   │   └── AuthenticationManager → DaoAuthenticationProvider        │     │
│   │       → UserDetailsService（查用户）→ PasswordEncoder（比对密码）  │     │
│   │ AuthorizationFilter                ③ 请求结束前：授权检查           │     │
│   │   └── SecurityContext 为空 且 接口受保护 → 401                    │     │
│   └───────────────────────────────────────────────────────────────┘     │
│                      │                                                   │
│                      ▼                                                   │
│                Controller（AuthController / 业务接口）                     │
│                      │                                                   │
│        ┌─────────────┼──────────────────┐                               │
│        ▼             ▼                  ▼                               │
│  UserDetailsService  HttpSession   Spring Session 仓库                  │
│        │          （Servlet 容器）      └──▶ Redis                       │
│        ▼                                 (spring:session:lab:sessions:*)│
│     MySQL（用户表）                                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

四个要点：

1. **认证状态 = SecurityContext**：`SecurityContextHolder` 基于 ThreadLocal，
   请求期间任何代码可读，请求结束自动清空
2. **没有任何自定义过滤器**：Session 认证的每个环节都是 Spring Security
   内置能力——所以本模块的 `SecurityConfig` 只有配置，没有 Java 实现类
3. **用户数据与会话数据分离**：用户存在 MySQL（`UserDetailsService`），
   会话存在 Redis（Spring Session），互不相干
4. **Redis 只是"会话仓库"而非"认证组件"**：把 Redis 换成内存或数据库，
   认证逻辑一行不用改——机制没变，存储换了

### 4.2 完整流程：请求在过滤器链中怎么走

#### ① 登录请求 POST /api/session/login

```
浏览器 POST /api/session/login {username, password}
  │
  ▼
过滤器链：UsernamePasswordAuthenticationFilter 拦截（登录处理 URL）
  │ ① 把 username/password 包装成 UsernamePasswordAuthenticationToken（未认证）
  │ ② AuthenticationManager.authenticate() → DaoAuthenticationProvider
  │ ③ UserDetailsService.loadUserByUsername("admin")   ← 查 MySQL
  │ ④ PasswordEncoder.matches("123456", bcrypt 哈希)   ← 比对密码
  │ ⑤ 认证成功 → 返回已认证的 Authentication
  │ ⑥ 框架把 Authentication 放入 SecurityContextHolder
  │ ⑦ successHandler 执行 → 返回 JSON 200 {"username":"admin"}
  ▼
请求收尾（框架自动）：
  │ ⑧ HttpSession 被创建（首次访问）→ 生成 JSESSIONID_LAB
  │ ⑨ SecurityContext 存入 HttpSession（HttpSessionSecurityContextRepository）
  │ ⑩ Spring Session 把 HttpSession 持久化到 Redis（每次访问续期 TTL）
  │ ⑪ 响应头 Set-Cookie: JSESSIONID_LAB=<随机串>; HttpOnly; SameSite=Lax
```

对照机制第 2.1 节：③（创建会话）在第 ⑧⑨⑩ 步完成，④（下发 Cookie）
在第 ⑪ 步完成。

#### ② 后续请求 GET /api/profile

```
浏览器自动携带 Cookie: JSESSIONID_LAB=xxx
  │
  ▼
过滤器链：SecurityContextHolderFilter（请求一开始就执行）
  │ ① 从 Cookie 取出 Session ID
  │ ② Spring Session 从 Redis 加载 HttpSession（查不到 → 视为未登录）
  │ ③ 从 Session 属性中取出 SPRING_SECURITY_CONTEXT
  │ ④ 放入 SecurityContextHolder（ThreadLocal）
  │ ⑤ 请求期间任何代码都能拿到当前用户
  ▼
AuthorizationFilter（授权检查）：
  │ ⑥ /api/profile 需要认证 → SecurityContext 里有 → 放行
  ▼
Controller → 200 OK
  │
请求结束（框架自动）：
  │ ⑦ SecurityContext 有变更才写回 Session；SecurityContextHolder 清空
```

对照机制第 2.2 节：②（取 ID）→ ③（查会话）→ ④（恢复身份）分别对应
上面的 ①②③④。注意第 ⑦ 步：**HttpSession 本身不销毁**，只是请求级的
SecurityContextHolder 被清空。

#### ③ 登出 POST /api/session/logout

```
过滤器链：LogoutFilter 拦截
  │ ① SecurityContextLogoutHandler：
  │    a. SecurityContextHolder.clearContext()
  │    b. session.invalidate() → 删除 Redis 中的会话记录
  │ ② 响应 Set-Cookie: JSESSIONID_LAB=; Max-Age=0 → 浏览器删除 Cookie
  │ ③ logoutSuccessHandler 返回 JSON 200
```

对照机制第 2.3 节：②（删除会话）在 ①b，③（客户端删票据）在 ②。

---

> 机制步骤与框架组件的对应关系（如"查用户 → `UserDetailsService`"、
> "生成会话 → `HttpSession` + Redis"），以及各配置项的详细说明，
> 都写在 `session-auth` 模块的代码注释中——直接打开
> `SecurityConfig.java` 和 `RedisConfig.java` 看，比查文档更直观。

---

## 5. 关联阅读

- [OpaqueToken认证原理](OpaqueToken认证原理.md) —— Session ID 与 Opaque Token 同构，可对照阅读
- [JWT认证原理](JWT认证原理.md) —— 无状态方案，与"有状态"的 Session 对比
- [Cookie全面指南](../Cookie全面指南.md) —— Cookie 的传输细节
- [Session全面指南](../Session全面指南.md) —— 本项目 Session 模块的完整指南
- [SecurityContext 生命周期](../spring-security/SecurityContext生命周期.md) —— 框架内部细节
