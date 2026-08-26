# OAuth2 认证原理

> **阅读建议**：第 1~3 章讲 OAuth2 **协议机制本身**——四类角色、授权码
> 流程、为什么安全，这些是 RFC 6749 定义的标准行为，与任何框架无关；
> 第 4 章才是"Spring Security 是如何完成这套操作的"，结合本项目的
> `oauth2-auth` 三个模块看效果更佳。

---

## 1. 机制本质：委托授权（不是认证）

前三篇的 Session / JWT / Opaque Token 解决的是同一个问题：
**"你是谁？"（认证）**。

OAuth2 解决的是另一个问题：
**"你授权第三方应用访问你的资源吗？"（授权）**。

```
典型场景：小明（资源所有者）想用"网易云"（客户端）读取他存在
"微信"（授权服务器 + 资源服务器）上的歌单。
         —— 网易云不该拿到微信的账号密码，
         —— 微信也不该把账号密码给网易云，
         —— 但小明确实想授权网易云读歌单。
```

OAuth2 的设计目标：**第三方应用在用户明确同意的前提下，用一张
"受限的通行证"（Token）访问用户资源——全程不接触用户的账号密码。**

所以 OAuth2 里有**四个角色**：

| 角色           | 解释                     | 例子        |
|--------------|------------------------|-----------|
| 资源所有者（用户）   | 拥有资源的人，决定授权给谁         | 小明        |
| 客户端          | 请求访问资源的第三方应用          | 网易云       |
| 授权服务器        | 验证用户身份、征得同意、签发 Token  | 微信        |
| 资源服务器        | 保管资源、验证 Token 后放行      | 微信的服务器    |

> 注意：OAuth2 本身**不做认证**——授权服务器在签发 Token 前当然要
> 确认"用户是谁"（它用自己的认证机制，比如 Session），但这是授权服务器
> 内部的事，协议不管。严格区分"认证"与"授权"是理解 OAuth2 的第一步。
> 在认证之上做"用户身份传递"，那是 OpenID Connect（OIDC）的事（见 3.6）。

---

## 2. 授权码模式（Authorization Code）完整流程

OAuth2 定义了多种授权模式，其中最安全、最常用的是**授权码模式**，
本项目演示的正是它。完整流程分两个阶段、四条通道：

```
┌─────────────────────────────────────────────────────────────────────┐
│                    阶段一：拿"兑换券"（授权码）                           │
│                                                                     │
│  用户/浏览器           客户端               授权服务器                    │
│     │① 发起登录 ──────▶│                    │                         │
│     │                  │② 302 重定向 ──────▶│  授权端点 /authorize     │
│     │◀─③ 浏览器跟随 ────│                    │   ?response_type=code    │
│     │                  │                    │   &client_id=...        │
│     │④ 输入账号密码 ────▶│                    │   &redirect_uri=...     │
│     │                  │                    │   &state=随机串          │
│     │                  │                    │                         │
│     │                  │                    │⑤ 验证身份（授权服务器自己的认证）│
│     │                  │                    │⑥ 展示授权页 → 用户点"同意"   │
│     │⑦ 同意授权 ──────▶│                    │                         │
│     │                  │                    │⑦ 生成一次性授权码 code     │
│     │◀─⑧ 302 回调 ─────│                    │   + 原样带回 state        │
│     │   Location: 客户端/callback?code=xxx&state=yyy                  │
│     │                  │                    │                         │
│     └── 以上走"浏览器通道"，授权码会经过浏览器（但码本身无价值）─────────────┘
│                                                                     │
│                    阶段二：拿 Token（不经过浏览器！）                      │
│                                                                     │
│  客户端（后端）                                     授权服务器             │
│     │⑨ POST /token ───────────────────────────────────▶│ 令牌端点      │
│     │   Authorization: Basic client_id:client_secret   │              │
│     │   grant_type=authorization_code                  │              │
│     │   &code=xxx &redirect_uri=...                    │              │
│     │                                                  │⑩ 校验：       │
│     │                                                  │   ① code 有效 │
│     │                                                  │   ② client 凭据│
│     │                                                  │   ③ redirect_uri│
│     │◀─────────── access_token + refresh_token ────────│              │
│     └── 这是"服务器对服务器"的后端通道，Token 全程不经过浏览器 ──────────────┘
│                                                                     │
│                    阶段三：用 Token 访问资源                              │
│                                                                     │
│  客户端（后端）                                    资源服务器              │
│     │⑪ GET /api/items ──────────────────────────────▶│              │
│     │   Authorization: Bearer <access_token>          │              │
│     │                                                  │⑫ 验证 Token： │
│     │                                                  │   验签/查存储、│
│     │                                                  │   检查 scope   │
│     │◀────────────── 200 OK 资源数据 ─────────────────│              │
└─────────────────────────────────────────────────────────────────────┘
```

### 关键细节

**① 为什么阶段一（授权码）要经过浏览器？**
因为"征求用户同意"这件事必须发生在用户面前——浏览器是用户与授权服务器
互动的通道。

**② 为什么阶段二（换 Token）必须不经过浏览器？**
因为 Token 权限大、价值高。如果 Token 直接发给浏览器，它可能被脚本、
被浏览器历史、被开发者工具看到。授权码模式让 Token 只在**客户端后端与
授权服务器之间**传递，浏览器全程看不到 Token——这就是它比
Implicit（隐式）模式安全的根本原因（见 3.1）。

**③ 授权码（code）为什么可以安全地经过浏览器？**
code 是**一次性**的、**短效**的（5~10 分钟）、并且**与 client_id 和
redirect_uri 绑定**的。就算被截获，没有 client_secret 也换不到 Token。
这就是"用低价值的码走高风险通道，用高价值的 Token 走低风险通道"
的隔离设计。

**④ state 参数是干什么的？**
state 是客户端发起授权时生成的随机串，授权服务器在回调时**原样带回**。
客户端校验"回来的 state == 我发的 state"，用来防 **CSRF**：
防止攻击者诱导用户完成一个用户没发起的授权回调。

**⑤ 为什么 client_secret 只能放在后端？**
它是对客户端身份的证明。放在浏览器里等于公开了身份凭证，任何人都能
冒充这个客户端去换 Token。所以阶段二必须是后端通道。

---

## 3. 设计要点

### 3.1 为什么授权码模式比 Implicit 模式安全

| 对比项       | 授权码模式（Authorization Code）      | 隐式模式（Implicit，已废弃）          |
|-----------|--------------------------------|-----------------------------|
| Token 传递通道 | 后端通道（服务器到服务器），浏览器看不到        | URL Fragment（#access_token=...） |
| client_secret | 可在服务端使用，证明客户端身份             | 无法使用（暴露在浏览器）              |
| Token 可见性  | ✅ 浏览器全程看不到 Token            | ❌ Token 出现在地址栏/历史记录中      |
| 风险         | 低（需同时拿到 code + secret 才能换）    | 高（Token 直接被浏览器持有）          |

授权码模式的本质是：**把"用户同意"和"Token 交付"拆到两条隔离的通道**，
各走各的安全等级。现在的 OAuth2 服务（GitHub、Google、微信）都只推荐
授权码模式，并在其上加 PKCE 进一步加固（见 3.4）。

### 3.2 Token 与 scope：一张"受限的通行证"

Token 不是"你是谁"的证明，而是"你被允许访问什么"的证明。授权服务器
签发的 Token 带 **scope**（权限范围）：

```
GET /authorize?scope=openid+profile+read
                    ↑        ↑       ↑
                    └─ 客户端声明自己需要的权限，用户逐项同意
```

- **最小权限原则**：客户端只申请自己真正需要的 scope，用户也只该同意必要的
- 资源服务器按 scope 放行：有 `read` 才能读资源，没有就拒绝
- Token 的 scope 是**用户同意过**的，客户端不能擅自扩大

### 3.3 Access Token 与 Refresh Token（RFC 6749 的标准定义）

双 Token 模式是 **OAuth2 协议的一等公民**（RFC 6749 §1.5 定义、§6 定义
刷新流程），不是 JWT 的概念。容易混淆的原因与正确边界，
见 [JWT认证原理](JWT认证原理.md) 第 4.1 节——简单说：
**JWT 是"格式"，OAuth2 是"协议"，Refresh Token 属于协议层**。

|               | Access Token                      | Refresh Token                     |
|--------------|----------------------------------|----------------------------------|
| 定义处        | RFC 6749 §1.4                    | RFC 6749 §1.5（§6 是刷新流程）       |
| 用途          | 访问资源服务器的资源                    | 向授权服务器换取新的 Access Token      |
| 发给谁        | 资源服务器（每次请求都带）                 | **只有授权服务器**（仅换新时用）          |
| 有效期        | 短（几分钟 ~ 1 小时）                  | 长（数天 ~ 数月）                    |
| 格式          | 可 JWT 可 Opaque（部署自定）           | 规范建议对客户端不透明；主流实现都是 Opaque  |
| 能否吊销       | 看格式（Opaque 能，JWT 难）           | 授权服务器**随时可以吊销**              |

关键事实（来自 RFC 6749）：

- Refresh Token 是**可选**签发的——授权服务器可以只发 Access Token
- Refresh Token 代表"资源所有者授予客户端的授权"，所以它**只和授权服务器
  打交道**，绝不发给资源服务器
- 刷新请求必须携带客户端身份（client_id + client_secret），换到的
  Access Token 的 scope 只能**相同或更窄**（不能扩大授权）
- 换新时授权服务器**可以轮换** Refresh Token（旧的作废），进一步控制风险

主流实现形态（Spring Authorization Server 实证）：

- **Access Token**：`TokenSettings.accessTokenFormat` 可选
  `SELF_CONTAINED`（JWT）或 `REFERENCE`（Opaque）
- **Refresh Token**：**永远是 Opaque**——随机值 + 服务端
  `OAuth2AuthorizationService` 记录，每次换新都查库校验、吊销即删
- 所以"OAuth2 的 Access Token 是 JWT"只是常见配置，不是协议要求；
  Access Token 同样可以做成 Opaque（可撤销），见
  [OpaqueToken认证原理](OpaqueToken认证原理.md)

### 3.4 PKCE：没有 client_secret 时的加固

公开客户端（原生 App、SPA）无法安全持有 client_secret，PKCE 协议
解决这个问题：客户端先自生成一个随机 `code_verifier`，把它的哈希
`code_challenge` 随授权请求发出；换 Token 时带上原始 `code_verifier`，
授权服务器校验哈希匹配。**即使授权码被截获，没有 verifier 也换不到
Token**——相当于给授权码加了一道"只有发起者知道的口令"。

### 3.5 授权模式怎么选

| 模式              | 适用客户端        | 安全性    | 状态    |
|-----------------|---------------|--------|-------|
| 授权码（Authorization Code） | Web 后端、三方登录   | 最高     | 标准推荐 |
| 授权码 + PKCE    | 原生 App、SPA     | 最高     | 推荐   |
| 客户端凭证（Client Credentials） | 服务间调用（无用户）   | 高      | 常用   |
| 设备码（Device Code） | 电视/智能设备无键盘输入  | 高      | 标准   |
| 隐式（Implicit）  | （历史遗留）        | 低      | 已废弃 |

### 3.6 OAuth2 ≠ 认证：还需要 OIDC

OAuth2 只解决"授权访问资源"，**不告诉你"用户是谁"**。
如果客户端需要拿到用户的身份信息（用户名、头像、邮箱），协议标准做法
是 **OpenID Connect（OIDC）**：在 OAuth2 之上增加一个 `openid` scope
和 `userinfo` 端点，客户端用 Token 换取标准化的用户身份声明。

> 本项目既演示了标准 OAuth2（GitHub/Google 登录），也演示了 OIDC：
> 授权服务器提供 `/userinfo` 端点，客户端用它获取当前登录用户信息。

### 3.7 优缺点总结

**优点：**

- 用户凭证不经过第三方应用——密码只交给信任的授权服务器
- 授权粒度细（scope）+ 可随时撤销（吊销 Token）——控制权在用户
- 标准协议：客户端一次接入，可对接所有遵循标准的服务
- 支持跨域、跨设备、第三方生态——互联网开放授权的基石

**缺点：**

- 流程复杂、角色多、端点多，实现和维护成本高
- 多个服务协作，出问题时排查链路长
- 依赖授权服务器可用性（它挂了，登录就全挂）
- 协议细节多（state、PKCE、redirect_uri 匹配……），配置错误即安全漏洞

---

## 4. Spring Security 如何实现 OAuth2

> 本章开始出现框架概念。Spring Security 对 OAuth2 的四个角色分别提供了
> 开箱即用的支持，本项目 `oauth2-auth` 正好是三个独立服务。
> 先看架构图建立整体认知，再走一遍完整流程。
> （每个服务的配置说明都写在对应模块的代码注释里，打开
> `SecurityConfig.java` / `AuthorizationServerConfig.java` 即可看到）

### 4.1 架构图：三服务组件全景

```
┌──────────────┐
│    浏览器      │  (Vue SPA，只与 client-server 同源交互)
└──────┬───────┘
       │ ① 点击"Lab 登录" → 302 跳转授权服务器（浏览器通道）
       │ ③ 授权回调 → 302 回到 client-server
       ▼
┌─────────────────────────── client-server（Port 18087）──────────────────────────┐
│  过滤器链：                                                                      │
│  OAuth2AuthorizationRequestRedirectFilter                                        │
│    ① 发起授权：构建授权 URL（client_id / redirect_uri / state）→ 302              │
│  OAuth2LoginAuthenticationFilter                                                 │
│    ③ 拦截回调：校验 state → ② 后端通道换 Token → 调 userinfo                       │
│  CustomOAuth2UserService / CustomOidcUserService                                 │
│    ⑤ 用户信息映射 + 本地账号绑定 → 建立本地登录 Session                            │
│  业务 Controller（登录页、绑定页、页面接口）                                       │
└───────┬──────────────────────────────────┬──────────────────────────────────────┘
        │ ② 后端通道（服务器对服务器）         │ ⑥ Authorization: Bearer <access_token>
        │    POST /api/oauth2-auth/token    │     （携带 Token 访问资源）
        │    （client_id + secret + code）  ▼
        ▼                                 ┌──────────────────────────────────────┐
┌───────────────────── auth-server（18085）│─────┐         resource-server（18086）│
│  登录页（认证用户身份）                     │     │  BearerTokenAuthenticationFilter │
│  授权页（征求用户同意，                     │     │    └─ 提取 Bearer Token         │
│    AuthorizationConsentController）        │     │  JwtAuthenticationProvider     │
│  AuthorizationServerConfig 四件套：        │     │    └─ NimbusJwtDecoder 验签     │
│    RegisteredClientRepository（DB）      │     │       └─ 公钥 ← /jwks（自动发现） │
│    OAuth2AuthorizationService（内存）    │     │  授权检查：SCOPE_read            │
│    JWKSource（RSA 私钥签名）             │     └──────────────────────────────────┘
│    AuthorizationServerSettings（端点）   │
│      /authorize /token /jwks /userinfo   │
└────────────────┬─────────────────────────┘
                 │ 客户端注册数据（oauth2_registered_client 表）
                 ▼
              MySQL
```

四个要点：

1. **client-server 承担协议里"客户端"的全部职责**：发起授权、拦截回调、
   后端换 Token、取用户信息——浏览器只经历"跳走"和"跳回"两个 302，
   **从头到尾看不到 Token**
2. **auth-server 是唯一"认识用户"的服务**：登录页、授权页都在这里
   （它内部用自己的 Session 认证确认用户身份，那是它自己的事）
3. **resource-server 不保存任何用户信息**：只做"验签 + 查 scope"，
   所以它可以完全无状态、任意扩容
4. **两条"带外"链路是协作关键**：后端通道（client ↔ auth-server 换
   Token）+ 公钥发现（resource-server ↔ auth-server 的 `/jwks`）——
   **验签的信任链**：auth-server 私钥签名 → `/jwks` 发布公钥 →
   resource-server 拿公钥验签

### 4.2 完整流程：一次三方登录在三服务间怎么走

```
① 浏览器点击"Lab 登录"
   → client-server 的 OAuth2AuthorizationRequestRedirectFilter
     构建授权 URL（response_type=code&client_id=...&redirect_uri=...&state=随机串）
   → 302 重定向到 auth-server 的 /authorize
      │
      ▼（浏览器通道）
② 用户在 auth-server 的登录页输入账号密码（auth-server 用自己的认证确认身份）
   → 授权页展示"lab-client 请求以下权限：openid, profile, read"
   → 用户点击同意
   → auth-server 生成一次性授权码 code（存入 OAuth2AuthorizationService）
      │
      ▼（浏览器通道）
③ 302 回 client-server 回调 URL：?code=xxx&state=yyy
      │
      ▼（进入 client-server 过滤器链）
④ OAuth2LoginAuthenticationFilter 拦截回调：
   a. 校验 state（防 CSRF）
   b. ★ 后端通道：POST auth-server 的 /token
      （Authorization: Basic client_id:client_secret, body: grant_type=authorization_code&code=xxx）
      —— 这一步是服务器对服务器，浏览器看不到
   c. auth-server 校验 code + 客户端凭据 → 返回
      { access_token(JWT, RSA 签名), refresh_token(Opaque), scope }
   d. client-server 用 access_token 调 auth-server 的 /userinfo（OIDC）
      获取用户身份
      │
      ▼
⑤ CustomOAuth2UserService / CustomOidcUserService 处理用户信息：
   → 已绑定本地账号 → 建立本地登录 Session，登录完成
   → 未绑定 → 暂存信息，跳转绑定页
      │
      ▼（之后的业务请求）
⑥ client-server 携带 access_token 调 resource-server 的 /api/oauth2-resource/items
   → BearerTokenAuthenticationFilter 提取 Token
   → JwtAuthenticationProvider 用 /jwks 拉到的公钥验签
   → 检查 SCOPE_read → 放行 → 返回资源
```

对照协议第 2 章：①② 对应"阶段一"的授权码获取，③ 是回调，④ 是
"阶段二"的换 Token，⑥ 是"阶段三"的访问资源。唯一多出来的 ⑤ 是
OIDC 部分——用 Token 换用户身份。

### 4.3 每个服务的配置在代码注释里

协议步骤与框架组件的对应关系（如"发起授权 → `OAuth2AuthorizationRequestRedirectFilter`"、
"验签 → `JwtAuthenticationProvider`"），以及每个服务的具体配置
（`oauth2Login` 配置、授权服务器四件套、`oauth2ResourceServer` 一行接入），
都写在对应模块的代码注释中——直接打开 `SecurityConfig.java` 和
`AuthorizationServerConfig.java` 看，比查文档更直观：

| 服务           | 看哪个文件                                                           |
|--------------|----------------------------------------------------------------|
| client-server | `SecurityConfig.java`（oauth2Login 流程注释）、`CustomOAuth2UserService.java` / `CustomOidcUserService.java`（绑定逻辑） |
| auth-server   | `AuthorizationServerConfig.java`（四件套 + 端点）、`DefaultSecurityConfig.java`（登录/授权页） |
| resource-server | `SecurityConfig.java`（`oauth2ResourceServer()` 接入与 scope 检查） |

---

## 5. 关联阅读

- [JWT认证原理](JWT认证原理.md) —— OAuth2 的 Access Token 常以 JWT 承载，验签方式见其第 4.4 节
- [OpaqueToken认证原理](OpaqueToken认证原理.md) —— Access Token 也可做成 Opaque，可撤销
- [Session认证原理](Session认证原理.md) —— 授权服务器自身通常用 Session 认证用户（机制内部的事）
- [OAuth2 接入指南](OAUTH2-SETUP.md) —— 本项目接入 GitHub / Google 三方登录的实操
- [OAuth2认证流程与多环境部署指南](../OAuth2认证流程与多环境部署指南.md) —— 本项目完整部署文档
- [Filter 执行顺序](../spring-security/Filter执行顺序.md) —— 客户端/资源服务器的过滤器链
