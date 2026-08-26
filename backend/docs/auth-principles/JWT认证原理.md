# JWT 认证原理

> **阅读建议**：第 1~3 章讲 JWT 认证**机制本身**——与任何框架无关，
> 重点理解"无状态、自包含、本地验签"这三个核心概念；
> 第 4 章才是"Spring Security 是如何完成这套操作的"，结合本项目的
> `jwt-auth` 模块看效果更佳。

---

## 1. 机制本质：自包含的签名凭据

Session 认证把状态存在**服务器**，客户端只拿一把钥匙。JWT 反其道而行：
**把用户状态（身份、权限、过期时间）直接装进凭据本身，交给客户端保存**，
服务器验证时不查任何存储，只做一次**本地验签**。

```
核心思想：凭据自带全部信息 + 数字签名保证不可伪造 → 服务器无需存储任何状态
```

类比一张**防伪证件**（护照/盖章通行证）：

| 类比对象  | 证件场景                            | JWT 认证场景                        |
|-------|----------------------------------|--------------------------------|
| 证件内容 | 姓名、照片、有效期……                      | Payload：用户标识、权限、过期时间……        |
| 防伪手段 | 激光防伪/水印，伪造即被发现                 | 数字签名，篡改即验签失败                |
| 查验    | 检查员看一眼防伪标记，不用打电话回发证机关核实      | 服务器本地重算签名比对，不查数据库/Redis    |
| 销毁    | 证件没过期就没法"作废"（只能等它过期）          | Token 没过期就没法主动失效（只能引入黑名单） |

**关键认知**：JWT 认证的"无状态"，是指**服务器不保存凭据与身份的对应关系**。
代价是：无法主动吊销、无法实时踢人——这是它和 Session/Opaque Token 的
根本分水岭。

---

## 2. JWT 结构

JWT 是一个**三段式字符串**，用 `.` 分隔：`Header.Payload.Signature`。

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.5ZvF8Kc...

┌──────────────────────────────────────────────────────────────┐
│ Header（头部）— 元信息：用什么算法签的                           │
│   {"alg": "HS256", "typ": "JWT"}                             │
│   → Base64Url 编码                                            │
├──────────────────────────────────────────────────────────────┤
│ Payload（载荷 / Claims）— 核心数据：用户是谁、何时过期            │
│   {                                                        │
│     "sub": "admin",              ← 用户标识（必填）            │
│     "iss": "security-jwt-auth-server",  ← 签发者             │
│     "iat": 1754000000,           ← 签发时间                    │
│     "exp": 1754003600,           ← 过期时间（必须）             │
│     "aud": "security-lab",       ← 受众                       │
│     "jti": "8f3a...",            ← 唯一 ID（防重放）           │
│     "authorities": ["ROLE_ADMIN"]  ← 自定义声明：权限           │
│   }                                                        │
│   → Base64Url 编码                                            │
├──────────────────────────────────────────────────────────────┤
│ Signature（签名）— 防伪标记，保证前两段未被篡改                   │
│   HMACSHA256( Header + "." + Payload ,  secret )             │
│   → Base64Url 编码                                            │
└──────────────────────────────────────────────────────────────┘
```

三个要点：

1. **Header 和 Payload 只是 Base64 编码，不是加密**。任何人拿到 Token
   都能解码看到内容——所以**不要把密码、手机号等敏感信息放进 Payload**。
2. **Signature 才是安全核心**。签名是拿 `Header.Payload` 加上**密钥**
   算出来的；不知道密钥就伪造不出合法签名；篡改 Payload 任何一个字节，
   重算出的签名立刻不匹配。
3. **算法不匹配是无效的**。Token 里声明了 `alg=HS256`，服务器必须用
   `HS256` 去验——所以服务器只信任自己配置的算法（防止 `alg=none`
   之类的降级攻击）。

---

## 3. 完整认证流程

### 3.1 登录：签发 JWT

```
① 客户端 POST /login，提交凭证 {username, password}
      ↓
② 服务器验证凭证（查用户 + 比对密码哈希）
      ↓
③ 验证通过 → 服务器签发 JWT：
   - 组装 Claims：sub=admin、authorities=[...]、iat=now、exp=now+TTL
   - 用密钥对 Header.Payload 计算签名
   - 拼成 eyJhbGci...eyJzdWI...5ZvF8...
      ↓
④ 服务器把 JWT 放进响应体返回（本流程不走 Cookie！）
   { "accessToken": "eyJhbGci...", "tokenType": "Bearer", "expiresIn": 3600000 }
      ↓
⑤ 客户端自己决定怎么存（内存 / localStorage）
```

关键区别：**服务器不保存任何与这个 Token 对应的记录**。签发完就忘，
后续谁拿这个 Token 来都能验，服务器不需要（也无法）区分"这个 Token
是我签给谁的"。

### 3.2 访问受保护资源：携带 JWT

```
① 客户端从存储中取出 Token
      ↓
② 放入请求头：Authorization: Bearer <token>
      ↓
③ 服务器提取 Token，做三步验证（全部本地计算，零存储查询）：
   a. 验签：用密钥重算 HMAC，与 Token 自带签名比对 → 防篡改/防伪造
   b. 验期：exp > 当前时间 → 防过期复用
   c. 提取 Claims → 得到 sub、authorities
      ↓
④ 服务器信任验证结果，按 Claims 中的身份和权限处理请求
```

**这是 JWT 认证最核心的一步**：整个验证过程不查数据库、不查 Redis，
只依赖"密钥只有服务器知道"这一前提。所以在分布式场景下，
任何一个持有同一密钥的服务都能独立验证 Token——这就是 JWT 对
微服务架构友好的原因。

### 3.3 登出：无操作（这就是问题）

```
Session 登出：删服务器会话记录 → 立即失效 ✅
JWT 登出：   Token 还在客户端手里，服务器无法让它失效 ❌
             只能：
             a) 客户端丢弃 Token（防己方后续携带，防不了泄漏/盗用）
             b) 服务器维护黑名单（又变回有状态，丢失无状态优势）
```

### 3.4 一张图看全流程

```
客户端                               服务器（签发 + 验证在同一服务）
  │  POST /login                      │
  │  {username, password}             │
  │ ─────────────────────────────────▶│
  │                                   │ ① 验证凭证
  │                                   │ ② 组装 Claims + 密钥签名
  │  {accessToken: "eyJhbGci..."}     │
  │ ◀─────────────────────────────────│
  │                                   │
  │  GET /api/profile                 │
  │  Authorization: Bearer eyJhbGci...│
  │ ─────────────────────────────────▶│
  │                                   │ ③ 本地验签（不查存储）
  │                                   │ ④ 检查 exp
  │                                   │ ⑤ 读取 Claims 恢复身份
  │  200 OK {username: "admin"}       │
  │ ◀─────────────────────────────────│
```

---

## 4. 设计要点

### 4.1 常见误区澄清：Refresh Token 不属于 JWT

很多 JWT 教程都会讲"Access Token + Refresh Token 双 Token 方案"，
容易让人误以为 Refresh Token 是 JWT 的一部分。**事实是**：

- **JWT（RFC 7519）只定义了 Token 的格式和注册声明**（iss / sub / aud /
  exp / iat / jti），规范里完全没有"Refresh Token"这个概念
- **Refresh Token 是 OAuth 2.0（RFC 6749 §1.5）的概念**：授权服务器签发给
  客户端、用来在 Access Token 过期后换取新 Token 的凭据
- JWT 教程里的"双 Token"模式，是自建认证系统**借用 OAuth2 的模式**，
  与 JWT 标准本身无关

| 概念            | 归属     | 定义处                    | 说明                |
|---------------|--------|------------------------|-------------------|
| JWT 格式与注册声明  | JWT 标准 | RFC 7519               | 只定义"Token 长什么样"     |
| Access Token  | OAuth2 | RFC 6749 §1.4          | 常以 JWT 承载，也可 Opaque |
| Refresh Token | OAuth2 | RFC 6749 §1.5 / §6     | 只在授权服务器之间流转        |

规范层面的 Refresh Token 通常做成**不透明字符串**，而不是 JWT：

- 它必须能被随时吊销 → 需要服务端校验 → 不需要"自包含"特性
- 它只在"换新 Token"时用一次 → 无需频繁传输，体积无所谓
- 实证：Spring Authorization Server 的 Refresh Token **永远是 Opaque**
  （随机值 + 服务端存储），只有 Access Token 可选 JWT 格式

所以正确的表述是：**JWT 解决"访问"（Access Token 的形态与验签），
Refresh Token 是 OAuth2 的"续期"机制**。本项目的 `jwt-auth` 采用
单 Token 方案，刻意不引入 Refresh Token——JWT 机制本身不含续期概念；
需要续期时应该走 OAuth2 的 `grant_type=refresh_token` 流程，
详见 [OAuth2认证原理](OAuth2认证原理.md) 第 3.3 节。

### 4.2 "无状态"意味着什么

```
Session/Opaque：状态在服务器 → 能踢人、能实时改权限、能统计在线
JWT：           状态在 Token 里 → 签发即生效、过期前无法收回
```

无状态的收益：服务器零存储、天然适配分布式、跨域友好。
无状态的代价：**无法主动失效**。账号被封禁/改密/登出，已签发的 Token
在过期前依然有效。生产方案是引入黑名单/白名单——但那就又变回有状态了。
选型时要想清楚：你要的到底是"无状态"，还是"不用查库"。

### 4.3 Payload 不是机密

Base64 解码即可见。所以：

- ✅ 放：用户 ID、用户名、角色/权限、过期时间
- ❌ 不放：密码、手机号、身份证、任何个人敏感数据

### 4.4 密钥管理：对称 vs 非对称

| 算法家族   | 验签方持有              | 适用场景                        |
|--------|---------------------|-----------------------------|
| HMAC   | 签发/验证用**同一个密钥**    | 单服务（本项目：HS256 + 一个 secret） |
| RSA/EC | 签发用**私钥**，验证用**公钥** | 多服务（授权服务器签，资源服务器验，见 OAuth2） |

非对称场景下，公钥可以公开分发（如 OAuth2 的 JWKS 端点），
资源服务器只信公钥、不需要接触签名私钥——这是 OAuth2 资源服务器
验签的标准做法（见 [OAuth2认证原理](OAuth2认证原理.md) 第 4.1 节的"验签的信任链"）。

### 4.5 优缺点总结

**优点：**

- 无状态：服务器不存凭据，天然适合分布式/微服务、横向扩容
- 本地验签：验证零网络开销（对比 Opaque Token 每次查 Redis）
- 自包含：一次验签同时拿到身份 + 权限，不查库
- 跨域友好：走 Authorization 头，不依赖 Cookie

**缺点：**

- 无法主动失效（过期前踢不掉人）—— 最核心的缺点
- Payload 明文可读（不能放敏感信息）
- Token 体积大（200+ 字符），每个请求都带着，增加带宽
- 客户端需要手动管理 Token（存储、续期、失效处理）
- 密钥泄露 = 全网伪造，密钥管理责任重

**适用场景**：API 服务、前后端分离 SPA、移动端、微服务间认证；
**不适用**：需要强管控（踢人/封禁即时生效）、需要审计在线状态的场景。

### 4.6 安全注意事项清单

1. 密钥必须随机、足够长、仅服务器持有——泄露即全部 Token 可伪造
2. Payload 不放敏感信息（只是 Base64）
3. 必须设 `exp`，且 Access Token 尽量短
4. 固定算法白名单，拒绝 `alg=none`/任意算法（防降级攻击）
5. HTTPS 必须——Token 在 Authorization 头中明文传输
6. 存储：localStorage 有 XSS 风险；对安全性敏感建议内存/HttpOnly Cookie
7. 需要主动失效时：黑名单（有状态化）或换用 Opaque Token

---

## 5. Spring Security 如何实现 JWT 认证

> 本章开始出现框架概念。**注意：Spring Security 本身不提供 JWT 能力**——
> JWT 是格式与算法，框架只提供"认证结果如何贯穿请求"的机制。
> 实现分两半：`JwtUtil`（JWT 本身）+ 自定义过滤器（把 JWT 接入框架）。
> 先看架构图，再走流程。

### 5.1 架构图：组件全景

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      jwt-auth 单服务（Port 18083）                        │
│                                                                         │
│   浏览器 ──HTTP──▶ 过滤器链                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │ JwtAuthenticationFilter（自定义 @Component）                      │   │
│   │   └── 职责：提取 JWT → 验签 → 恢复认证状态 → 放行                  │   │
│   │ UsernamePasswordAuthenticationFilter（框架，本模块空转）           │   │
│   │   └── 表单登录过滤器；登录走自定义接口，它在链上但不处理            │   │
│   │ AuthorizationFilter（框架）                                      │   │
│   │   └── 职责：SecurityContext 为空 且 接口受保护 → 401              │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                     │                                                    │
│      ┌──────────────┼───────────────────────────┐                        │
│      ▼              ▼                           ▼                        │
│  JwtUtil（jjwt）  Controller               UserDetailsService            │
│   └── 密钥 secret   （AuthController 签发 JWT）  └──▶ MySQL（用户表）       │
│       (application.yml)                                                 │
│                                                                         │
│  无 Session · 无 Redis · STATELESS（每次请求独立认证，请求结束状态消失）    │
└─────────────────────────────────────────────────────────────────────────┘
```

四个要点：

1. **职责分离**：机制（格式/验签）在 `JwtUtil`，框架接入（过滤器 +
   SecurityContext）在 `JwtAuthenticationFilter`
2. **链上唯一新增的是自定义过滤器**，其余全部框架内置
3. **认证状态只活在单个请求内**（ThreadLocal），请求结束即消失——
   这就是 STATELESS 的落地
4. **登录与访问是两条路**：登录走 Controller 签发 JWT；访问走过滤器
   验签恢复身份

### 5.2 完整流程

#### ① 登录 POST /api/auth/login（签发）

```
浏览器 POST /api/auth/login {username, password}
  │
  ▼
AuthController：
  │ ① AuthenticationManager.authenticate()
  │    → UserDetailsService.loadUserByUsername()（查 MySQL）
  │    → PasswordEncoder.matches()（比对密码）
  │ ② 认证成功 → JwtUtil.generateAccessToken(userDetails)
  │    → 组装 Claims（sub / authorities / exp…）→ 密钥签名 → JWT 字符串
  │ ③ 返回 JSON {accessToken, tokenType, expiresIn}
  ▼
前端保存 Token（内存 / localStorage）
```

对照机制第 3.1 节：③（签发）在 ②，④（返回 Token）在 ③。

#### ② 访问 GET /api/profile（验签恢复）

```
浏览器请求头: Authorization: Bearer eyJhbGci...
  │
  ▼
过滤器链：JwtAuthenticationFilter（自定义，请求一进来就执行）
  │ ① 取 Authorization 头 → ② 检查 "Bearer " 前缀 → ③ 截取 JWT 字符串
  │ ④ jwtUtil.extractUsername() ← 内部已验签（签名不匹配即抛异常）
  │ ⑤ SecurityContext 已有认证？→ 有则跳过（避免重复处理）
  │ ⑥ UserDetailsService 查库 → 确认账号状态、拿最新权限
  │ ⑦ jwtUtil.isTokenValid()（用户名比对 + exp 检查）
  │ ⑧ jwtUtil.extractAuthorities() 从 Claims 取权限
  │ ⑨ 创建 3 参数 UsernamePasswordAuthenticationToken（= 已认证）
  │ ⑩ 设置 details（IP 等审计信息）
  │ ⑪ SecurityContextHolder.setAuthentication() ★ 认证恢复完成
  │ ⑫ filterChain.doFilter() 放行
  ▼
AuthorizationFilter：SecurityContext 里有认证 → 放行
  ▼
Controller → 200 OK
  │
请求结束：SecurityContextHolder 自动清空（没有 Session 可存，状态随之消失）
```

对照机制第 3.2 节：③（验签+验期）在 ④⑦，④（信任 Claims）在 ⑧⑨⑪。

#### ③ 过滤器"只认人、不拒人"的设计

Token 无效/缺失时过滤器**静默放行**（不拦截），由后续 `AuthorizationFilter`
统一处理：SecurityContext 为空 + 接口受保护 → 401。好处：公开接口
（无 Token 可访问）与受保护接口（无 Token 被拒）共用一个过滤器链。

---

> 机制步骤与代码的对应关系（如"组装 Claims + 签名 → `JwtUtil.generateAccessToken()`"、
> "验签 → `JwtUtil.parseToken()`"），以及签发/验签代码、过滤器接入方式等
> 实现细节，都写在 `jwt-auth` 模块的代码注释中——直接打开
> `JwtUtil.java`、`JwtAuthenticationFilter.java`、`SecurityConfig.java`
> 看，比查文档更直观。

---

## 6. 关联阅读

- [OpaqueToken认证原理](OpaqueToken认证原理.md) —— 有状态方案，与 JWT 对照
- [Session认证原理](Session认证原理.md) —— 有状态方案，与 JWT 对照
- [OAuth2认证原理](OAuth2认证原理.md) —— 非对称验签（公钥验 JWT）的企业级场景
- [Authentication 生命周期](../spring-security/Authentication生命周期.md) —— SecurityContext 贯穿请求的细节
- [Filter 执行顺序](../spring-security/Filter执行顺序.md) —— 自定义过滤器挂在链上哪个位置
