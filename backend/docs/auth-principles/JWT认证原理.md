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
│     "jti": "8f3a...",            ← 唯一 ID（日志用）           │
│     "authorities": ["ROLE_ADMIN"]  ← 自定义声明：权限           │
│   }                                                        │
│   → Base64Url 编码                                            │
├──────────────────────────────────────────────────────────────┤
│ Signature（签名）— 防伪标记，保证前两段未被篡改                   │
│   HMACSHA256( Header + "." + Payload ,  secret )             │
│   → Base64Url 编码                                            │
└──────────────────────────────────────────────────────────────┘
```

> 图中标"必填 / 必须"的声明，是**本项目按安全与运维需要主动填满的**，规范并不强制 —— 判据见 2.1。

三个要点：

1. **Header 和 Payload 只是 Base64 编码，不是加密**。任何人拿到 Token
   都能解码看到内容——所以**不要把密码、手机号等敏感信息放进 Payload**。
2. **Signature 才是安全核心**。签名是拿 `Header.Payload` 加上**密钥**
   算出来的；不知道密钥就伪造不出合法签名；篡改 Payload 任何一个字节，
   重算出的签名立刻不匹配。
3. **算法不匹配是无效的**。Token 里声明了 `alg=HS256`，服务器必须用
   `HS256` 去验——所以服务器只信任自己配置的算法（防止 `alg=none`
   之类的降级攻击）。

### 2.1 哪些声明是必填的？（先破一个常见误解）

**结论：RFC 7519 一个都不强制。** 规范原文（§4.1）：

> "None of the claims defined below are intended to be mandatory to use or implement in all cases…"

这些名字之所以"看起来必须"，是因为它们被**注册（registered）**了——名字、类型、语义全部规定死
（`exp` / `nbf` / `iat` 必须是**秒级**数字时间戳，`aud` 可以是字符串或数组），
所以各语言库才为它们提供了类型化方法（jjwt 的 `.subject()` / `.expiration()` …）。
**"有定义"不等于"必须出现"。** 实测（jjwt 0.12.6）：

```
Jwts.builder().claim("hello", "world").signWith(key, Jwts.SIG.HS256).compact()
  → {"hello":"world"}     ← 没有 sub / iss / aud / iat / exp / jti
  → 解析器正常解析，sub 取出来是 null，没有任何"缺少必填声明"的报错
```

那"必填"从哪来？**只有两个来源**：

| 来源 | 说明 |
|---|---|
| **你的校验端** | 解析时 `require` 了什么，什么就真的必填。本项目 `JwtUtil.parseToken()` 里的 `requireIssuer` / `requireAudience` 就是干这个的 —— **这是唯一的技术真源** |
| **你遵循的 profile** | OIDC 身份令牌：`iss` / `sub` / `aud` / `exp` / `iat`；RFC 9068 JWT 访问令牌：`iss` / `exp` / `aud` / `sub` / `client_id` / `iat` / `jti` |

一句话记法：

> **规范规定字段"怎么写"，你规定哪些"必须写"；你 require 了的，才真的必填。**

（另有两个"条件强制"值得知道：`exp` / `nbf` 一旦出现，校验方 **必须** 检查；
同一个 issuer 面向多个接收方时，RFC 8725 要求签发方 **必须** 带 `aud` 且接收方 **必须** 验。）

### 2.2 本项目的选择：这 4 个必须填

| 声明 | 填？ | 不填会怎样 |
|---|---|---|
| `exp` | ✅ 必须 | 永不过期的凭据 = 永久后门 |
| `iss` | ✅ 必须 | 不知道谁签的；多系统共用密钥时无法区分 |
| `aud` | ✅ 必须 | A 系统签的 Token 能拿去打 B 系统 |
| `sub` | ✅ 必须 | 校验端不知道"这是谁" |
| `iat` / `jti` | 建议 | 排查问题、算凭据年龄 / 日志追踪、将来接黑名单 |
| 自定义业务字段 | 越少越好 | 体积、泄密、快照过期 |

它们之所以"该填"，是**安全和运维的需要**，不是哪份规范拿枪指着你 ——
逐行代码注释见 `jwt-auth/.../util/JwtUtil.java#generateAccessToken()`。

### 2.3 载荷里放什么、不放什么

| | 内容 | 原因 |
|---|---|---|
| ✅ | 稳定的身份标识（`sub`）、权限、受众（`aud`） | 这正是"自包含"的意义：校验端不查库就能用 |
| ❌ | 敏感信息（手机号、身份证、内部备注…） | Payload 只是 Base64Url，**不是加密**，谁都能解 |
| ❌ | 易变信息（余额、昵称、频繁变更的权限） | 签发即快照：改不了、撤不回，只能等 `exp` |
| ❌ | 大块数据 | Token 每个请求都背在请求头上（Tomcat / Nginx 默认 8KB 上限） |

> JWT 本质上做不到的三件事及对策：**撤销** → 短 TTL + 黑名单（对照 `opaque-auth` 模块）；
> **保密** → JWE（加密，5 段式）；**防重放** → 服务端存已用 `jti`（本模块未做，`jti` 目前只用于日志追踪）。

### 2.4 三段是怎么造出来的？怎么验的？（用真实密钥手算一遍）

先纠正一个词：**JWT 是"签名"，不是"加密"，所以没有"解密"这一步。**
三段里只有第三段涉及密码学，而且只用了**一个**运算：`HMAC-SHA256`。
第二段谁都能读（下面第 4 条会看到）——签名只保证"没被改过、确实是我签的"。

#### ① 怎么造出来：3 步

```
Claims（一个 JSON 对象）
    ↓ 1. JSON 序列化成 UTF-8 字节                     ← 到这里还是明文
   {"sub":"admin","authorities":["ROLE_ADMIN"],"aud":["security-lab"],
    "iss":"security-jwt-auth-server","iat":1754000000,"exp":1754003600,"jti":"8f3a…"}
    ↓ 2. Base64Url 编码（换一种文本表示，任何人都能还原）
   eyJzdWIiOiJhZG1pbiIsImF1dGhvcml0aWVzIjpbIlJPTEVfQURNSU4iXS…
    ↓ 3. 与 Header 段拼起来（中间加一个点），对这段字符串做 HMAC-SHA256，结果再 Base64Url
   HMAC-SHA256("段1.段2", 密钥) → Mic8TBoVlGLRMvATR5v19QzLRGfukR5m2GX0cw8l_uw
    ↓
段1.段2.段3  ← 最终 Token
```

Header 段同样这么来：`{"alg":"HS256"}` → Base64Url → `eyJhbGciOiJIUzI1NiJ9`。

实测（本模块配置里的 `app.jwt.secret`，jjwt 0.12.6）：

| 段 | 值 |
|---|---|
| 段1 Header | `eyJhbGciOiJIUzI1NiJ9` → 解码 `{"alg":"HS256"}` |
| 段2 Payload | `eyJzdWIiOiJhZG1pbiIsImF1dGhvcml0aWVzIjpbIlJPTEVfQURNSU4iXSwiYXVkIjpbInNlY3VyaXR5LWxhYiJdLCJpc3MiOiJzZWN1cml0eS1qd3QtYXV0aC1zZXJ2ZXIiLCJpYXQiOjE3NTQwMDAwMDAsImV4cCI6MTc1NDAwMzYwMCwianRpIjoiOGYzYTFjNWUtMDAwMC00MDAwLTgwMDAtMDAwMDAwMDAwMDAxIn0` |
| 段3 Signature | `Mic8TBoVlGLRMvATR5v19QzLRGfukR5m2GX0cw8l_uw` |
| **手工**用 `Mac.getInstance("HmacSHA256")` 对 `段1.段2` 算出的值 | `Mic8TBoVlGLRMvATR5v19QzLRGfukR5m2GX0cw8l_uw` ← **与段3逐字符相同** |

也就是说：**第三段 = HMAC-SHA256(段1 + "." + 段2, 密钥)，再 Base64Url**，没有别的花样。
代码里对应 `JwtUtil.generateAccessToken()` 的 `.signWith(signingKey, Jwts.SIG.HS256)`。

#### ② 怎么验：3 步，全程本地计算

```
1. 切：按 . 切成三段
2. 算：用自己手里的密钥，对 "段1.段2" 重算一遍 HMAC-SHA256
3. 比：重算值 == 段3 ？相等 → 内容没被改过；不等 → 拒绝（jjwt 抛 SignatureException）
   随后再检查 exp 是否过期、iss / aud 是否匹配（判据见 2.1）
```

实测篡改：把段2 解码后的 `"admin"` 改成 `"root"`、段3 照抄旧的 →
`用旧签名验得过吗：false`，jjwt 抛 `SignatureException`。

#### ③ 没有密钥也能读前两段（所以"解密"这件事不存在）

```
不提供任何密钥，Base64Url 解码段2 →
{"sub":"admin","authorities":["ROLE_ADMIN"],"aud":["security-lab"],"iss":"security-jwt-auth-server","iat":1754000000,"exp":1754003600,"jti":"8f3a…"}
```

Payload 从来没有被加密，只是换了文本编码。所以"不要往 Payload 放敏感信息"不是建议，
是它**根本不提供保密性**。真需要保密要用 **JWE**（5 段结构，Payload 是密文，那才叫加解密）。

> 顺带一个实测细节：上面的 Header 解码后只有 `{"alg":"HS256"}`，**没有 `typ`** ——
> jjwt 0.12 默认不写它；RFC 8725 §3.11 建议显式声明 `typ` 以防类型混淆。

#### ④ 两个容易踩的细节

**密钥不是那串可见字符。** 配置里存的是 Base64，代码里先解码再当密钥用
（`JwtUtil` 构造函数的 `Decoders.BASE64.decode(secret)`）：

```
配置字符串 : c2VjdXJlLXNlY3JldC1mb3Itand0LWF1dGgtc2VydmVyLTIwMjQtYmxhYmxh
解码后字节 : secure-secret-for-jwt-auth-server-2024-blabla   （45 字节 = 360 位；HS256 下限 256 位）
```

拿字符串本身当密钥算出来的是 `LfrpV1NYcyCFBSuEf-N8C8uxlmpsRqFprXotOQWTw48`，**验不过**。
之所以用 Base64 存：HMAC 密钥是任意二进制，Base64 让它能安全地写进 yml。

**Base64Url ≠ Base64**，两处差别都是为了能安全塞进 URL / Header：

| | 标准 Base64 | Base64Url（JWT 用） |
|---|---|---|
| 字母表 | `+` `/` | `-` `_` |
| 补位 | 用 `=` 补到 4 的倍数 | **去掉** `=` |
| 同一串字节 | `+///`、`AQ==` | `-___`、`AQ` |

#### ⑤ HS256 和 RS256 的区别（本仓库两种都有）

| | HS256（`jwt-auth` 模块） | RS256（`oauth2-auth` 模块） |
|---|---|---|
| 密钥 | 对称密钥，签和验用同一把 | 私钥签、公钥验（JWKS 分发） |
| 段3 怎么来 | `HMAC-SHA256(段1.段2, 密钥)` | `RSA-SHA256(段1.段2, 私钥)` |
| 谁能签发 | 任何拿到密钥的人 | 只有持有私钥的签发方 |
| 适用 | 签发方 = 校验方（单体） | 多服务共享验签，校验方拿不到签发权 |

---

## 3. 完整认证流程

### 3.1 登录：签发 JWT

```
① 客户端 POST /login，提交凭证 {username, password}
      ↓
② 服务器验证凭证（查用户 + 比对密码哈希）
      ↓
③ 验证通过 → 服务器签发 JWT：
  - 组装 Claims：sub=admin、authorities=[...]、iss、aud、iat=now、exp=now+TTL、jti
  - 用 HS256 + 密钥对 Header.Payload 计算签名
  - 拼成 eyJhbGci...eyJzdWI...5ZvF8...
     ↓
④ 服务器把 JWT 放进响应体返回（本流程不走 Cookie！）
  { "token": "eyJhbGci...", "tokenType": "Bearer", "expiresIn": 3600 }
  （expiresIn 单位是**秒** —— RFC 6749 惯例；配置里写的是毫秒，接口层负责换算）
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
   c. 校验 iss / aud，然后提取 Claims → 得到 sub、authorities
      ↓
④ 服务器信任验证结果，按 Claims 中的身份和权限处理请求
```

**这是 JWT 认证最核心的一步**：整个验证过程不查数据库、不查 Redis，
只依赖"密钥只有服务器知道"这一前提。所以在分布式场景下，
任何一个持有同一密钥的服务都能独立验证 Token——这就是 JWT 对
微服务架构友好的原因。

> **本仓库 `jwt-auth` 模块的取舍**：它的过滤器在上面这步之外，还回查了一次数据库
> ——复查 enabled / locked 等账号状态、并以 DB 权限为准。好处是**禁用账号、调整角色立即生效**，
> 代价是每请求一次查询。上面描述的"零存储查询"才是纯 JWT 形态：去掉过滤器里
> 第 5.2 节的 ⑥⑦⑧ 三步、直接信任 Claims 即可（撤销就只能等 exp 到期，取舍见 3.3 节与 `opaque-auth` 模块）。

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
  │  {token: "eyJhbGci..."}             │
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
  │ ③ 返回 JSON {token, tokenType, expiresIn}
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
  │ ④ jwtUtil.parseToken() ← 一次解析完成验签 + 验期 + 校验 iss/aud（抛异常即 Token 无效）
  │ ⑤ 取 Claims.sub；SecurityContext 已有认证？→ 有则跳过（避免重复处理）
  │ ⑥ UserDetailsService 查库（权威数据源：账号状态 + 权限）
  │ ⑦ 复查账号状态：enabled / accountNonLocked / accountNonExpired / credentialsNonExpired
  │ ⑧ 权限取 DB 当前值（Token 里的 authorities 只作快照，差异记 DEBUG 日志）
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

对照机制第 3.2 节：③（验签 + 验期 + 校验 iss/aud）在 ④；⑥⑦⑧ 是"回查权威数据源"的补充动作
（机制里刻意没有这一步，它是本模块为"撤销立即生效"付的代价）；④（信任结果）落在 ⑨⑪。

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
