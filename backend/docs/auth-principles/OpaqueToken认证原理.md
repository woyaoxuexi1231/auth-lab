# Opaque Token 认证原理

> **阅读建议**：第 1~3 章讲 Opaque Token 认证**机制本身**——不依赖任何框架，
> 它的核心就一句话："凭据本身不携带任何信息，身份状态存在服务器，验证靠查询"；
> 第 4 章才是"Spring Security 是如何完成这套操作的"，结合本项目的
> `opaque-auth` 模块看效果更佳。

---

## 1. 机制本质：不透明凭据 + 服务端存储

Opaque Token（不透明令牌）认证是**有状态**方案：登录成功后，服务器生成
一串**随机字符串**（通常是 UUID）作为凭据发给客户端，同时在服务端存储
"这串字符串 ↔ 用户身份"的对应关系。后续请求携带这串字符串，服务器查
存储找回身份。

```
核心思想：Token 只是一把"钥匙"，所有信息都在服务器上。
"不透明" = 你无法从 Token 本身读出任何信息。
```

类比**储物柜存包**：

| 类比对象 | 储物柜场景                    | Opaque Token 场景             |
|------|--------------------------|---------------------------|
| 凭据   | 存包小票（一张纸条）               | UUID 随机字符串（如 550e8400-...） |
| 存储   | 柜子和你的包                   | Redis/数据库中的 Token 记录      |
| 验证   | 凭小票找管理员开柜                | 拿 Token 查服务端存储            |
| 作废   | 小票撕掉/柜子清空 → 立即失效        | 删除存储记录 → 立即失效            |
| 信息   | 小票上不写你包里有什么             | Token 里不含任何用户信息           |

**和 Session ID 是同构的**：Session ID 本质就是一个 Opaque Token——
随机字符串、服务端存储、查询验证、删除即失效。区别只在于存储内容的
组织和使用的语境（见 3.1 的对比）。而**和 JWT 是对立的**：JWT 把信息
装进 Token（自包含、本地验签），Opaque Token 把信息留在服务器（查询
验签、可撤销）。

---

## 2. 完整认证流程

参与方三个：**客户端**、**服务器**、**Token 存储**（Redis/数据库）。
三个阶段：**登录签发 → 携带访问 → 登出撤销**。

### 2.1 登录：签发 Token

```
① 客户端 POST /login，提交凭证 {username, password}
      ↓
② 服务器验证凭证（查用户 + 比对密码哈希）
      ↓
③ 验证通过 → 服务器生成一个随机 Token（UUID）
      ↓
④ 服务器把用户身份写入存储：
   Key:   <token>          （或带前缀，如 opaque:token:<uuid>）
   Value: {"username":"admin", "authorities":[...]}
   TTL:   7200 秒
      ↓
⑤ 服务器只把 Token 字符串返回给客户端
   { "token": "550e8400-e29b-41d4-a716-446655440000",
     "expiresIn": 7200 }
      ↓
⑥ 客户端保存 Token（内存 / localStorage / 安全存储）
```

注意：返回的 Token **不含任何用户信息**。`admin`、`ROLE_ADMIN` 这些
数据从没离开过服务器（第④步只写在存储里）。

### 2.2 访问受保护资源：查询验证

```
① 客户端取出 Token，放入请求头：Authorization: Bearer <token>
      ↓
② 服务器提取 Token
      ↓
③ 服务器用 Token 查询存储：
   - 查到记录 → Token 有效 → 读取身份和权限 → 恢复登录状态
   - 查不到（无效/已过期/已撤销）→ 拒绝访问
```

**这一步是 Opaque Token 与 JWT 的本质分界点**：

```
JWT 验证：   本地重算签名比对 —— 不访问任何存储
Opaque 验证： 拿 Token 查存储 —— 每次请求一次存储查询（Redis 通常 <1ms）
```

正是这一步"查询"，让服务器对凭据有了**完全的控制权**：
删掉记录，Token 立刻失效；改掉记录，权限立刻生效。

### 2.3 登出：撤销 Token

```
① 客户端 POST /logout（携带 Token）
      ↓
② 服务器删除存储中的对应记录
      ↓
③ 该 Token 立即失效 —— 即使客户端还持有它，下次请求也查不到
```

对比 JWT 的登出（无操作、只能等过期），这就是 Opaque Token
被称为"可撤销方案"的原因。

### 2.4 一张图看全流程

```
客户端                         服务器                     Token 存储(Redis)
  │  POST /login              │                           │
  │  {username, password}     │                           │
  │ ─────────────────────────▶│                           │
  │                           │ ① 验证凭证                  │
  │                           │ ② 生成 UUID Token          │
  │                           │ ③ 写入 Token 记录 ─────────▶│
  │                           │     (身份+权限+TTL)         │
  │  {token: "550e8400-..."}  │                           │
  │ ◀─────────────────────────│                           │
  │                           │                           │
  │  GET /api/profile         │                           │
  │  Authorization: Bearer ...│                           │
  │ ─────────────────────────▶│                           │
  │                           │ ④ 查 Token 记录 ──────────▶│
  │                           │ ◀────── 记录(有效) ────────│
  │                           │ ⑤ 身份恢复，处理请求          │
  │  200 OK {username:"admin"}│                           │
  │ ◀─────────────────────────│                           │
  │                           │                           │
  │  POST /logout             │                           │
  │  Authorization: Bearer ...│                           │
  │ ─────────────────────────▶│                           │
  │                           │ ⑥ 删除 Token 记录 ────────▶│
  │                           │     立即失效！              │
  │  200 OK                   │                           │
  │ ◀─────────────────────────│                           │
```

---

## 3. 设计要点

### 3.1 与 Session、JWT 的本质对比

| 维度      | Session（Session ID）   | Opaque Token        | JWT                 |
|---------|----------------------|--------------------|--------------------|
| 凭据形态    | 随机字符串（不透明）         | 随机字符串（不透明）        | 自包含三段式（可解码读出）     |
| 身份状态在哪 | 服务器会话存储              | 服务器 Token 存储       | 凭据自身（Payload）      |
| 验证方式    | 查会话存储                | 查 Token 存储         | 本地验签（零存储查询）       |
| 可主动撤销  | ✅ 能（删会话）            | ✅ 能（删 Token）       | ❌ 不能（只能等过期/黑名单） |
| 权限实时生效 | ✅ 能                  | ✅ 能                 | ❌ 不能（旧 Token 旧权限） |
| 服务器状态  | 有状态                  | 有状态                 | 无状态                |

Session ID 与 Opaque Token 是**同一模式的两个名字**，只是使用场景不同：
Session ID 由框架/容器管理、依托 Cookie 传递、粒度是"整个会话上下文"；
Opaque Token 由业务代码管理、走 Authorization 头、粒度是"一条登录凭证"。
它们的共同对立面是 JWT：**有状态 vs 无状态**。

### 3.2 为什么需要"查询"这一步

"查询"不是缺陷，而是**控制力的来源**：

- 服务器实时知道每个 Token 存在与否 → 可统计、可踢人、可封禁
- 记录的 Value 可以随时更新 → 改权限、改角色即时生效
- 删除即撤销 → 登出、改密码、账号异常处理都干净利落

代价只是每次请求多一次存储查询（Redis GET 通常 <1ms，可忽略）。

### 3.3 过期策略

| 策略    | 做法                                     | 适用场景            |
|-------|----------------------------------------|-----------------|
| 固定过期 | 签发时定 TTL，到点即失效                         | 高风险操作、严格安全要求  |
| 滑动过期 | 每次验证通过就重置 TTL（活跃则永续）                  | 常规 Web/App 会话习惯 |
| 主动撤销 | 随时删除记录（登出、封号、改密后）                    | 所有场景都建议支持      |

> 注意：Redis 的 TTL 过期是**惰性+定期**删除，Token 在存储中可能
> "过期了但记录还在"。验证时建议显式比对创建时间或依赖 Redis 的
> GET 返回 null 来判断——本项目 `getTokenInfo()` 直接以
> GET 结果是否为 null 判定有效，简单可靠。

### 3.4 存储选型

| 存储      | 优点                         | 缺点                    | 场景            |
|---------|----------------------------|-----------------------|-------------|
| Redis    | 快、原生 TTL、天然适合"凭据仓库"     | 重启丢数据（可开启持久化）       | 常规选择（本项目）    |
| 数据库     | 持久化、可查询、可审计              | 读写频繁压力大             | 需要审计/分析的场景   |
| 内存 Map  | 零依赖                      | 重启全丢、多实例不共享         | 仅演示/单机        |

Token 是"高读高频写、过期即弃"的数据，Redis 的 TTL 机制和内存访问
速度几乎是为这个场景定制的。

### 3.5 优缺点总结

**优点：**

- **可撤销**：登出/封禁/改密后，Token 删除即失效——国内大厂普遍选它的首要原因
- **权限实时生效**：管理员改权限，下一次请求立即生效（JWT 做不到）
- 实现简单：UUID + 存储读写，无签名算法、无密钥管理
- Token 体积小（UUID 36 字符 vs JWT 200+），移动端更友好
- 不透明 = 不泄露任何用户信息，Token 被截获也无法解读

**缺点：**

- **有状态**：服务器依赖存储，Redis 挂了认证就挂（需要高可用）
- 每次请求多一次存储查询（相比 JWT 本地验签）
- 分布式场景下所有服务要共享同一存储
- 无法脱离存储验证——不适用于"离线/去中心化"场景

**适用场景**：需要强管控的系统（可踢人、可封禁、权限常变）、
国内大厂 API 网关类系统、移动端应用；**不适用**：需要完全无状态、
或验证方无法访问共享存储的场景。

---

## 4. Spring Security 如何实现 Opaque Token 认证

> 本章开始出现框架概念。Spring Security 原生不提供 Opaque Token 支持
> （它内置的是 Session 和 JWT/Bearer 两种），所以需要自定义实现——
> 拆成两部分：`TokenService`（机制本身）+ `TokenAuthenticationFilter`
> （把机制接入框架）。先看架构图，再走流程。

### 4.1 架构图：组件全景

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    opaque-auth 单服务（Port 18088）                       │
│                                                                         │
│   浏览器 ──HTTP──▶ 过滤器链                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │ TokenAuthenticationFilter（自定义 @Component）                    │   │
│   │   └── 职责：提取 Token → 查 Redis → 恢复认证状态 → 放行            │   │
│   │ UsernamePasswordAuthenticationFilter（框架，本模块空转）           │   │
│   │   └── 表单登录过滤器；登录走自定义接口，它在链上但不处理            │   │
│   │ AuthorizationFilter（框架）                                      │   │
│   │   └── 职责：SecurityContext 为空 且 接口受保护 → 401              │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                     │                                                    │
│      ┌──────────────┼──────────────────────────────┐                     │
│      ▼              ▼                              ▼                     │
│  TokenService   Controller                 UserDetailsService           │
│   └── Redis      （登录签发 / 登出撤销）            └──▶ MySQL（用户表）     │
│     (opaque:token:* + TTL)                                               │
│                                                                         │
│  无 Session · 有状态（Redis 是认证状态的唯一来源）                          │
└─────────────────────────────────────────────────────────────────────────┘
```

四个要点：

1. **与 JWT 模块同构**：把上图里的 `TokenService → Redis` 换成
   `JwtUtil → 密钥`，就是 `jwt-auth` 的架构——**唯一差异是"验证"
   这一步的依赖**（查存储 vs 本地算）
2. **Redis 是认证状态的家**：Token 与身份的对应关系只存在 Redis 里，
   这就是"有状态"的落点；Redis 挂掉，认证即不可用
3. **登录与访问两条路**：登录走 Controller → `createToken` 写 Redis；
   访问走过滤器 → `getTokenInfo` 读 Redis
4. **无自定义"登录"过滤器**：凭证校验（用户名密码）仍然用框架内置的
   `AuthenticationManager` 体系，Token 相关的才是自定义

### 4.2 完整流程

#### ① 登录 POST /api/auth/login（签发 Token）

```
浏览器 POST /api/auth/login {username, password}
  │
  ▼
AuthController：
  │ ① AuthenticationManager.authenticate()
  │    → UserDetailsService.loadUserByUsername()（查 MySQL）
  │    → PasswordEncoder.matches()（比对密码）
  │ ② 认证成功 → TokenService.createToken(username, authorities)
  │    → UUID.randomUUID() 生成 Token
  │    → 身份 JSON 化 → 写入 Redis（SET key value EX 7200）
  │ ③ 返回 JSON {token, expiresIn}
  ▼
前端保存 Token
```

对照机制第 2.1 节：③（生成 Token）④（写存储）在 ②，⑤（返回）在 ③。

#### ② 访问 GET /api/profile（查询验证）

```
浏览器请求头: Authorization: Bearer 550e8400-e29b-41d4-a716-446655440000
  │
  ▼
过滤器链：TokenAuthenticationFilter（请求一进来就执行）
  │ ① 取 Authorization 头 → 检查 "Bearer " 前缀 → 截取 Token 字符串
  │ ② SecurityContext 已有认证？→ 有则跳过（避免重复处理）
  │ ③ TokenService.getTokenInfo(token)
  │    → GET opaque:token:<uuid>          ★ 查 Redis，与 JWT 的关键区别
  │    → Key 存在 → 反序列化出 {username, authorities}
  │    → Key 不存在（无效/过期/已撤销）→ 跳过认证
  │ ④ 用存储中的用户名/权限构建 3 参数 UsernamePasswordAuthenticationToken
  │ ⑤ 设置 details（IP 等审计信息）
  │ ⑥ SecurityContextHolder.setAuthentication() ★ 认证恢复完成
  │ ⑦ filterChain.doFilter() 放行
  ▼
AuthorizationFilter：SecurityContext 里有认证 → 放行
  ▼
Controller → 200 OK
```

对照机制第 2.2 节：③（查询验证）在 ③，④（恢复身份）在 ④⑥。
注意判定逻辑：**Key 存在 = Token 有效**（Redis TTL 到期 Key 自动消失，
所以"过期"与"撤销"走同一条判定路径）。

#### ③ 登出 POST /api/auth/logout（撤销 Token）

```
AuthController：TokenService.revokeToken(token) → DEL opaque:token:<uuid>
  → 记录删除，Token 立即失效 —— 即使客户端还持有它
```

对照机制第 2.3 节：②（删除记录）一步完成，这就是 Opaque 相比 JWT
"可撤销"的实现落点。

### 4.3 为什么 Spring Security 不内置 Opaque Token

框架内置的是 Session（查会话）和 JWT（验签）两个端点方案。Opaque Token
本质是"自定义凭据 + 自定义存储"，每个系统的存储结构、Key 设计、过期策略
都不同——框架无法给出一套统一实现，所以留出扩展点（过滤器链），
由业务代码把机制接进来。这也正是本项目两个模块（`jwt-auth`、
`opaque-auth`）存在的意义：**同样的框架，不同的机制选择**。

---

## 5. 关联阅读

- [JWT认证原理](JWT认证原理.md) —— 无状态对立面，本地验签 vs 查存储
- [Session认证原理](Session认证原理.md) —— 同构模式：Session ID 就是不透明凭据
- [OAuth2认证原理](OAuth2认证原理.md) —— OAuth2 的 Access Token 既可以是 JWT 也可以是 Opaque
- [Filter 执行顺序](../spring-security/Filter执行顺序.md) —— 自定义过滤器挂在链上哪个位置
