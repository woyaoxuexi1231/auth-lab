# Cookie 全面指南

## 什么是 Cookie

Cookie 是**服务器通过响应头发放到浏览器、浏览器保存后随每个请求自动回传**的一小段文本数据。
它的存在只有一个目的：在**刻意无状态的 HTTP 协议**上，让服务器能"记住"客户端。

```
浏览器 ──── 请求 ────→ 服务器
  ↑                      │
  │               服务器生成 Cookie
  │                      ↓
  │   Set-Cookie: xxx    │
  └──── 保存 Cookie ──────┘

浏览器 ──── 请求（自动携带 Cookie）────→ 服务器 → 识别身份
```

## 一、诞生：1994 年，Netscape 的电商困局

### 时代背景：Web 刚刚起步

1989 年 Tim Berners-Lee 在 CERN 提出万维网，1991 年 HTTP/0.9 问世。
早期的 Web 是**文档分享**系统：每个请求独立、看完就关、服务器什么都不记得。
浏览器只能"翻页"，无法"购物"——因为购物车需要跨请求记住状态，
而无状态 HTTP 下"每次访问都像第一次"（就像健忘症的店主）。

### 发明者：Lou Montulli

1994 年 6 月，Netscape 通讯公司（当时仅 9 名员工）的程序员 **Lou Montulli（卢·蒙图利，24 岁）**
受命解决这个问题。Netscape 正在开发一个电商应用（一个虚拟购物车，据说是为 MCI 公司做的），
必须让服务器在多次访问之间记住用户。

Montulli 写了一份五页的文档，提议：让服务器在访客的机器上放一个小文件，用来追踪该访客在本站的活动。
他正式的名字叫 **"Persistent Client State Object"（持久化客户端状态对象）**，
文件题为 *"Persistent Client State: HTTP Cookies"*。

### 名字的由来：Unix 的"魔法饼干"

Montulli 需要一个更顺口的名字，他借用了早期计算领域的一个老概念：**magic cookie（魔法饼干）**。

> 早在 1979 年，施乐 PARC（Xerox PARC）的程序（如 Fortune 程序）之间互相传递一种
> "程序收到后原样返回的数据包"用于身份识别，程序员们把它戏称为 magic cookie。
> 这个概念后来在 Unix 编程中广为流传（xauth 的认证令牌就是典型例子）。

Montulli 认为自己的发明正是这个概念的后代：**一段服务器下发、浏览器原样带回、服务器再识别**的数据。
于是就叫它 **cookie**。他后来回忆："饼干是给狗吃的，魔法饼干是被程序拿来传递的。"

### 首次面世

| 时间          | 事件                                                             |
|-------------|----------------------------------------------------------------|
| 1994 年 6 月   | Montulli 撰写 cookie 草案，并与 John Giannandrea 一起完成最初规范          |
| 1994 年 10 月 13 日 | **Mosaic Netscape 0.9beta 发布，首次支持 cookie**                     |
| 1994 年      | 第一个真实用途：检查访问者是否来过 Netscape 官网（"你是回头客吗"）              |
| 1995 年      | Montulli 申请专利（1998 年获批，美国专利 US 5774670）                   |
| 1995 年 10 月  | Internet Explorer 2.0 也加入 cookie 支持，事实标准形成                   |
| 1996 年 2 月 12 日 | 英国《金融时报》首次公开报道 cookie —— **公众才第一次知道它的存在**            |

### 早期的反对声

Cookie 公开后立刻引发隐私担忧。1996 年，**Vint Cerf（TCP/IP 之父）和 Tim Berners-Lee**
联合发表论文 *"An Analysis of the Security Problems in the Web and Their Solutions"*，
对"HTTP with State"方案提出强烈质疑——他们认为这违背了 Web 的简洁与隐私精神。
同期美国 FTC（联邦贸易委员会）也介入调查 cookie 追踪问题。
这些争议直接推动了一个重要结果：**cookie 进入了 IETF 标准化流程**。

## 二、规范化的历程：谁的规范？

Cookie 经历了四次规范化，最终由 IETF 以 RFC 形式确立：

```
1994  Netscape 草案（事实标准）
  │
  ▼
1997  RFC 2109  ← 首个 IETF 标准（Kristol & Montulli）
  │
  ▼
2000  RFC 2965  ← 重写 RFC 2109（Kristol & Montulli）—— 浏览器基本没采纳
  │
  ▼
2011  RFC 6265  ← 现行标准（Adam Barth，加州大学伯克利）—— 唯一的现行规范
```

### RFC 2109（1997 年 2 月）—— 第一个正式标准

- 作者：**D. Kristol（贝尔实验室）与 L. Montulli**
- 基于 Netscape 草案，第一次用正式语法（ABNF）定义了 `Set-Cookie` / `Cookie` 头
- 明确给出了**安全建议：应禁止/限制第三方 cookie 的默认使用**——这个担忧 30 年后仍未解决

### RFC 2965（2000 年 10 月）—— 一次失败的尝试

- 作者：Kristol 与 Montulli，取代 RFC 2109
- 引入了 `Cookie2` / `Set-Cookie2` 头和 `Port`（端口限定）属性，试图做"更强的规范"
- 结果：**浏览器厂商基本没有实现**。规范可以写得完美，但如果浏览器不听，就是废纸
- 最终被标记为 Historic（历史状态）

### RFC 6265（2011 年 4 月）—— 现行标准

- 作者：**Adam Barth**（加州大学伯克利），取代 RFC 2965
- 核心思路转变：**不再描述"应该怎么做"，而是描述"现实是怎么做的"**
  规范原文直言：Netscape 草案、RFC 2109、RFC 2965 **没有一个准确描述了互联网上 cookie 的真实用法**
- 正式废弃 `Cookie2` / `Set-Cookie2`
- 这就是今天所有浏览器、所有服务器实现所遵循的规范：**RFC 6265 "HTTP State Management Mechanism"（HTTP 状态管理机制）**

### RFC 6265 之后：规范仍在演进

2016 年起，IETF HTTP 工作组通过 **RFC 6265bis**（6265 的修订草案）持续补充：

| 新特性        | 提出时间 | 说明                                    |
|------------|------|---------------------------------------|
| SameSite 属性 | 2016（草案） | 控制跨站请求是否携带 cookie，Chrome 80 起默认 Lax  |
| `__Secure-` / `__Host-` 前缀 | 2020s | 强制要求属性（如必须 Secure），防"前缀注入"攻击        |
| Partitioned 属性 | 2022s | 顶级站点隔离，第三方 cookie 的隐私替代方案             |

> **一句话总结**：Cookie 的规范是 **RFC 6265**（IETF 制定），
> 前身是 RFC 2109 / RFC 2965 和 1994 年的 Netscape 草案。

## 三、工作原理

### 一次完整的交互

用"记住你的语言偏好"举例——不涉及任何登录/会话，纯粹看 Cookie 的行为：

第一次请求（服务器下发 Cookie）：
```
① 浏览器首次访问 GET /
   → 服务器看不到任何 Cookie：又是第一次见到你
② 这是一个支持多语言的站点，服务器通过响应头下发：
   HTTP/1.1 200 OK
   Set-Cookie: lang=zh-CN; Path=/; Max-Age=31536000
③ 浏览器把 cookie 存入 Cookie Store
```

后续请求（浏览器自动携带）：
```
① 用户第二天再次访问 GET /
② 请求头自动附带（浏览器替你做的，无需任何代码）：
   GET / HTTP/1.1
   Host: example.com
   Cookie: lang=zh-CN
③ 服务器看到 lang=zh-CN → 直接返回中文版页面，用户不用再选一次语言
```

> 关键认知：`Set-Cookie` 是**响应头**（服务器→浏览器），`Cookie` 是**请求头**（浏览器→服务器）。
> "携带 cookie"是浏览器的内建行为，只要域名、路径、协议满足匹配规则，就自动附带。

> 补充：会话管理是 Cookie 的经典用途之一——**Session 最终也由 Cookie 作为载体进行传输**
> （服务器把随机会话 ID 放进 Cookie，浏览器回传，服务器据此恢复登录状态）。
> 详细机制见 [Session 全面指南](Session全面指南.md)。

## 四、Cookie 的构成与属性

```
Set-Cookie: <name>=<value>; [属性1]; [属性2]; ...
```

| 属性          | 示例值                                   | 作用                                       |
|-------------|----------------------------------------|------------------------------------------|
| Name        | `lang`                                  | 名称（键）                                   |
| Value       | `zh-CN`                                 | 值（偏好、标记等数据）                          |
| Expires     | `Expires=Wed, 21 Oct 2026 07:28:00 GMT` | 绝对过期时间（到点删除，持久化 cookie）                |
| Max-Age     | `Max-Age=86400`                         | 相对过期秒数（0=立即删除；负值=会话期 cookie）           |
| Domain      | `Domain=example.com`                    | 允许携带该 cookie 的域名（含子域名）                 |
| Path        | `Path=/`                                | 允许携带该 cookie 的路径前缀                      |
| Secure      | `Secure`                                | 仅在 HTTPS 下传输                            |
| HttpOnly    | `HttpOnly`                              | 禁止 JavaScript 读取（防 XSS）                 |
| SameSite    | `SameSite=Lax`                          | 跨站请求是否携带（防 CSRF）                       |
| Partitioned | `Partitioned`                           | 顶级站点隔离（第三方 cookie 隐私方案）                |

### 关键细节

**Expires vs Max-Age**：两者同时出现时 **Max-Age 优先**；Max-Age=0 立即删除。

**会话期 vs 持久化**：

| 类型     | 是否设置 Expires/Max-Age | 删除时机            | 典型用途      |
|--------|----------------------|-----------------|-----------|
| 会话期 cookie | 否                    | 浏览器会话结束（通常为关闭浏览器） | 会话 ID   |
| 持久化 cookie | 是                    | 到期后删除            | 记住我、偏好设置 |

> 现代浏览器（尤其 Chrome）的"关闭浏览器"不再简单清空会话期 cookie（受"恢复会话"设置影响），
> 真正可靠的过期手段是 Max-Age/Expires。

**`__Secure-` / `__Host-` 前缀**（RFC 6265bis 的强化机制）：

```
Set-Cookie: __Secure-token=xxx; Secure            ← 强制要求 Secure 属性
Set-Cookie: __Host-token=xxx; Secure; Path=/      ← 强制 Secure + Path=/ + 无 Domain
```

前缀的意义：即使被"cookie 注入"攻击写入同名 cookie，带前缀的版本也会被浏览器强制校验属性，伪造不出高危配置。

## 五、作用域规则：cookie 什么时候会被携带

### Domain：属于哪个域名

```
Cookie: name=abc; Domain=example.com
携带场景：
  ✓ example.com        ✓ api.example.com        ✓ www.example.com
  ✗ other.com
```

- 未设置 Domain → **Host-only cookie**，只属于当前主机（不含子域名）
- 设置 Domain → 主域名与所有子域名共享

### Path：属于哪些路径

```
Cookie: name=abc; Path=/admin
携带场景：
  ✓ /admin/login   ✓ /admin/users
  ✗ /profile       ✗ /api/login
```

### SameSite：同站与跨站

判断"同站"看**站点**（eTLD+1，即"注册域"），与端口、子域名无关：
`app.example.com` 与 `api.example.com` 同站；`example.com` 与 `evil.com` 跨站。

| SameSite | 同站请求 | 跨站请求                     | 典型场景       |
|----------|------|--------------------------|------------|
| Strict   | ✓    | ✗ 永不携带                  | 银行等最高安全要求  |
| **Lax（默认）** | ✓    | 仅"顶层导航 GET"携带           | 通用 Web 应用推荐 |
| None     | ✓    | ✓ 总是携带（必须配合 Secure）     | 第三方登录、跨站 API |

```
SameSite=Lax 的实际行为：
  从别的网站点链接跳转过来（GET 顶层导航）→ 携带 ✓
  页面里 iframe / <img> / fetch 发起的跨站请求  → 不携带 ✗
```

## 六、第一方 Cookie vs 第三方 Cookie

| 类型     | 判定        | 例子                                | 现状           |
|--------|-----------|-----------------------------------|--------------|
| 第一方 cookie | 与当前站点同站   | 你在 example.com，example.com 下发并读取的 cookie | 正常使用         |
| 第三方 cookie | 由其他站点下发   | 你在 example.com，页面却收到并回传 ads.com 的 cookie | 被浏览器逐步封禁     |

第三方 cookie 是跨站广告追踪（Profile 画像）的技术基石——也正因为此，它成为整个隐私战场的中心（见第十一章）。

## 七、Cookie 的用途

| 用途       | 存储内容                        | 例子                        |
|----------|-----------------------------|---------------------------|
| 会话管理    | 会话 ID（指向服务器端 session）       | `JSESSIONID`（Java Servlet 规范约定名） |
| 个性化     | 偏好、主题、语言、地区                | `lang=zh-CN`、`theme=dark` |
| 追踪/分析   | 用户标识、行为标记                   | 广告平台的分析 cookie            |

## 八、Cookie vs 浏览器其他存储

| 维度       | Cookie          | localStorage | sessionStorage |
|----------|-----------------|--------------|----------------|
| 大小上限    | ~4KB            | 5~10MB       | 5~10MB         |
| 自动随请求携带 | ✓（浏览器自动附加）    | ✗（需手动塞）     | ✗              |
| 服务器可读  | ✓               | ✗            | ✗              |
| 生命周期   | 会话期 / Max-Age  | 永久           | 标签页关闭即消失      |
| 安全      | 可 HttpOnly+Secure | JS 全可读（XSS 风险） | 同左             |

> **结论**：认证凭据应该放 Cookie（+ HttpOnly + Secure），而不是 localStorage——
> 后者任何注入的脚本都能读走；前者 JavaScript 根本碰不到。

## 九、安全

### 攻击与防护对照

| 攻击     | 原理                          | Cookie 侧防护                    |
|--------|-----------------------------|-------------------------------|
| XSS 窃取  | 恶意脚本执行 `document.cookie`     | `HttpOnly`（JS 完全不可见）           |
| 中间人窃听  | 明文 HTTP 抓包                   | `Secure` + HTTPS                |
| CSRF    | 跨站伪造请求，浏览器自动带 cookie        | `SameSite` + 站点内 CSRF Token    |
| 会话劫持   | 偷走会话 cookie 冒充身份             | HttpOnly + Secure + 会话 ID 轮换    |
| 会话固定   | 预置自己的会话 ID 诱导受害者登录          | 登录成功后更换会话 ID（见 Session 文档）    |
| Cookie 投毒 | 向 `example.com` 注入/覆盖同名 cookie | `__Host-` 前缀 + 服务端签名校验         |

### 生产环境的安全配置基线

```
Set-Cookie: token=xxx
    ; Path=/                  ← 全站可用
    ; HttpOnly                ← 防 XSS
    ; Secure                  ← 仅 HTTPS
    ; SameSite=Lax            ← 防 CSRF（默认值，一般够用）
```

### 两条铁律

1. **Cookie 里不要放敏感数据**：cookie 随每个请求回传且可被客户端查看/篡改，
   明文密码、身份证号、手机号一律不放；要放就放"随机 ID + 服务端验签"。
2. **凭据类 cookie 必须 HttpOnly**：这是 XSS 与账户被盗之间的最后一道闸门。

## 十、隐私战争：从 DoubleClick 到第三方 Cookie 的黄昏

Cookie 发明 30 年，最大的争议不是技术，而是**追踪**。

### 第一阶段：广告追踪（1995–2010）

- 1995 年 DoubleClick 成立，开创基于 cookie 的跨站行为广告——同一广告商在你的所有访问站点标记你
- 1996 年 FTC 开始关注；2000 年前后"cookie 恐慌"达到顶点
- 行业对策：浏览器增加"禁止第三方 cookie"选项，但默认关闭，形同虚设

### 第二阶段：法律介入（2011–2018）

- **2011 年欧盟《Cookie 指令》**（2009/136/EC）：网站使用 cookie 必须事先获得用户同意——"欧洲网站弹窗"从此统治世界
- **2018 年 GDPR 生效**：cookie 被归入"个人数据"范畴，同意必须明确、可撤销；中国《个人信息保护法》（2021）同理

### 第三阶段：浏览器出手（2017–2023）

- **2017 年 Safari 推出 ITP**（智能防追踪）：开始默认拦截第三方 cookie，之后逐年收紧
- **2019 年 Firefox 推出 ETP**（增强追踪保护）：默认拦截
- **2020 年 1 月 Chrome 宣布**：两年内彻底弃用第三方 cookie，用"Privacy Sandbox"（隐私沙盒）替代——业界震动，广告业开始"后 cookie 时代"备战

### 第四阶段：Chrome 的反复与放弃（2024–2026）

- 2023–2024：弃用时间表一再推迟（监管与广告业压力）
- **2024 年 7 月：Google 宣布放弃弃用计划**，第三方 cookie 留在 Chrome
- **2025 年 4 月 22 日：Google 正式确认**——不弃用、也不做独立的"用户选择"弹窗，
  用户可在 Chrome 隐私设置中自行管理；无痕模式继续默认拦截
- **2025 年底：Privacy Sandbox 项目实质终结**——Topics、Protected Audience 等 API
  因采用率过低（Topics 仅约 13% 页面加载使用）被宣布弃用，Chrome 144 开始移除

### 2026 年的现状

```
Safari  默认拦截第三方 cookie ✓
Firefox 默认拦截第三方 cookie ✓
Chrome  保留第三方 cookie（不默认拦截，隐私设置中管理，无痕模式拦截）
→ 全球约一半浏览流量已经"无第三方 cookie"，广告业转向第一方数据 + 语境广告
```

> 历史讽刺：1997 年 RFC 2109 就建议"限制第三方 cookie"，直到 30 年后，这个建议才被浏览器强制执行——
> 而强制它的不是规范，而是市场（Safari/Firefox）和法律（GDPR）。

## 十一、参考资料

- **RFC 6265** — *HTTP State Management Mechanism*（现行标准，2011）https://www.rfc-editor.org/rfc/rfc6265.html
- **RFC 2965** — *HTTP State Management Mechanism*（历史状态，2000）https://datatracker.ietf.org/doc/rfc2965/
- **RFC 2109** — *HTTP State Management Mechanism*（历史状态，1997）
- **RFC 6265bis** — 6265 修订草案（SameSite、Cookie 前缀、Partitioned）
- Montulli 与 cookie 的发明史：《Giving Web a Memory Cost Its Users Privacy》（纽约时报，2001）https://www.nytimes.com/2001/09/04/business/giving-web-a-memory-cost-its-users-privacy.html
- OWASP Session Management Cheat Sheet — cookie 安全的最佳实践基准

## 相关文档

- [Session 全面指南](Session全面指南.md) — Cookie 最经典的用途：会话管理
- [Session 认证原理](Session认证原理.md) / [JWT 认证原理](JWT认证原理.md) — 本仓库各认证方案中的 cookie 应用
