# OAuth2 认证模块：完整流程与多环境部署指南

> 适用范围：`backend/java/spring-security-auth-lab/backend/oauth2-auth`（auth-server / resource-server / client-server）+ 统一网关 + Vue 前端 + Nginx
> 目标：把 OAuth2 授权码模式在 4 种部署模式下**每个 URL、每次 302 跳转、每个 Cookie 归属**讲清楚。
> 最后更新：2026-08

---

## 目录

1. [系统组成与端口](#1-系统组成与端口)
2. [必须先理解的 4 个概念](#2-必须先理解的-4-个概念)
3. [完整认证流程（本地开发逐跳展开）](#3-完整认证流程本地开发逐跳展开)
4. [前端页面、路由与环境变量](#4-前端页面路由与环境变量)
5. [配置项总览（每个配置在哪、影响什么）](#5-配置项总览每个配置在哪影响什么)
6. [四种部署模式详解](#6-四种部署模式详解)
7. [常见问题排查](#7-常见问题排查)
8. [配置速查表（按部署模式）](#8-配置速查表按部署模式)

---

## 1. 系统组成与端口

### 1.1 后端服务（全部注册到 Nacos，网关用 `lb://应用名` 转发）

| 角色 | 模块 | 端口 | 网关路由前缀 | 应用名（Nacos） |
|---|---|---|---|---|
| 统一网关 | `backend/java/gateway` | **18090** | — | security-gateway |
| 授权服务器（Auth Server） | `oauth2-auth/auth-server` | 18085 | `/api/oauth2-auth/**` | security-oauth2-auth-server |
| 资源服务器（Resource Server） | `oauth2-auth/resource-server` | 18086 | `/api/oauth2-resource/**` | security-oauth2-resource-server |
| OAuth2 客户端 / BFF（Client） | `oauth2-auth/client-server` | 18087 | `/api/oauth2-client/**` | security-oauth2-client |

> 网关路由**不剥前缀**：后端控制器自带完整前缀（如 `/api/oauth2-client/...`），网关只做匹配转发。

### 1.2 基础设施（`backend/java/pom.xml` 的 desktop/laptop profile 里配置）

| 组件 | desktop（台式机） | laptop（笔记本） |
|---|---|---|
| Nacos | 192.168.96.129:28848 | 192.168.6.128:28848 |
| MySQL | 192.168.96.129:3306（库 `security_lab_oauth2` / `security_lab_oauth2_client`） | 192.168.6.128:3306 |
| Redis | 192.168.96.129:6379 | 192.168.6.128:6379 |

### 1.3 前端

| 环境 | 启动/承载方式 | 访问地址 | Vite base |
|---|---|---|---|
| 开发 | `npm run dev`（Vite） | http://localhost:13000 | `/` |
| 生产 | Nginx 静态托管 + `/api` 反代 | http://\<host\>/app/ | `/app/` |
| 生产 | Nginx 静态托管 + `/api` 反代 | http://\<host\>/study/ | `/study/` |

---

## 2. 必须先理解的 4 个概念

这是排查"本地能跑、换环境就崩"问题的钥匙：

### 2.1 Cookie 是 host-only 的，与端口无关

浏览器 Cookie 按 **Host（不含端口）** 归属：

- 只要在 `localhost` 下发的 Cookie（如 `JSESSIONID_LAB`），浏览器访问 `localhost:13000` 和 `localhost:18090` **都会带**；
- 但访问 `192.168.3.4:18090`、`127.0.0.1:18090`、`backend.example.com` **不会带**。

> OAuth2 流程里 client-server 的 session（`JSESSIONID_LAB`）必须始终在**同一个 Host** 上建立和使用，否则会变成"回调成功但绑定页找不到会话"。

### 2.2 redirect_uri 必须逐字符一致

auth-server 注册的 `redirect-uri`、client-server 里 client registration 的 `redirect-uri`、浏览器实际回调的 URL，三者必须**完全一致**：

- `localhost` 与 `127.0.0.1` 视为不同；
- 多一个/少一个 `/` 都不行。

### 2.3 issuer 必须三方一致

auth-server 的 `spring.security.oauth2.authorizationserver.issuer`、client-server provider 的 `authorization-uri / token-uri / user-info-uri / jwk-set-uri`、resource-server 的 `issuer-uri`，全部基于同一个 `@oauth2.base-url@`。不一致会出现：

- token 换不到（endpoint 不通）；
- 验签失败（JWKS 拿不到）；
- `iss` 声明校验失败（issuer 不一致，401）。

### 2.4 浏览器可见 URL vs 服务端间 URL 是两套

- **浏览器可见**（授权页、consent、回调、绑定页）：必须填"浏览器能访问到的地址"；
- **服务端间调用**（token 交换、userinfo、JWKS）：必须填"后端进程能访问到的地址"。

在"全部本地 IDE"时两者都是 localhost，混用没感觉；一旦上 Docker 或跨服务器，这两套地址就分道扬镳了（详见第 6 章）。

---

## 3. 完整认证流程（本地开发逐跳展开）

> 本处以"全部本地 IDE"为例：前端 `localhost:13000`，网关 `localhost:18090`。每个 `302` 都会写出 Location。浏览器视角的所有地址，`vite` 代理（开发）或 nginx（生产）负责把 `/api/*` 转发到网关。

### 3.0 浏览器访问后端 API 的路径（开发 vs 生产）

| 请求（浏览器发出的 URL） | 开发时实际到达 | 生产时实际到达 |
|---|---|---|
| `http://localhost:13000/api/oauth2-client/...` | vite 代理 → `localhost:18090`（网关）→ client-server | nginx 反代 → 网关 → client-server |
| `http://localhost:13000/api/oauth2-auth/...` | vite 代理 → 网关 → auth-server | nginx 反代 → 网关 → auth-server |

### 3.1 本地账号登录（表单，不涉及 OAuth2）

1. 浏览器打开 `http://localhost:13000/#/auth-lab/oauth2`（前端 OAuth2Auth.vue）。
2. 点"登录" → `POST /api/oauth2-client/auth/login`（form-urlencoded，`credentials: include`）。
3. client-server 的 `formLogin` 处理：成功 → 200 `{"success":true,...}`，并下发 `JSESSIONID_LAB` Cookie（host=localhost）；失败 → 401 JSON。
4. 前端拿到成功后再 `GET /api/oauth2-client/profile` 刷新用户信息。

### 3.2 OAuth2 "Lab 登录"完整流程（含重定向、含未绑定跳绑定页）

这是本项目最关键的流程。**客户端角色** = client-server，**授权服务器角色** = auth-server。

| 步骤 | 谁发起 | 请求/跳转 | 关键点 |
|---|---|---|---|
| 1 | 前端 | 点"Lab 登录" → `window.open('/api/oauth2-client/authorization/lab-client')`，弹窗打开 `http://localhost:13000/api/oauth2-client/authorization/lab-client` | 相对路径，落在前端源 |
| 2 | vite 代理 | `localhost:13000` → `localhost:18090`（网关）→ client-server | 浏览器无感知 |
| 3 | client-server | 生成 authorization request，**存进自己的 session**（`Set-Cookie: JSESSIONID_LAB=...`，host=localhost），`302 → http://localhost:18090/api/oauth2-auth/authorize?response_type=code&client_id=lab-client&redirect_uri=http%3A%2F%2Flocalhost%3A18090%2Fapi%2Foauth2-client%2Flogin%2Foauth2%2Fcode%2Flab-client&scope=openid+profile+read&state=<随机>` | state 与 redirect_uri 都存 session 了 |
| 4 | 弹窗跟随 302 | 经网关到 auth-server `/api/oauth2-auth/authorize` | 浏览器访问的是 `localhost:18090`，`JSESSIONID_LAB` 还在（host 相同） |
| 5 | auth-server | 未登录 → `302 /api/oauth2-auth/login`（相对路径，浏览器拼成 `http://localhost:18090/api/oauth2-auth/login`） | 登录页是 auth-server 自己的 Thymeleaf 页面 |
| 6 | 用户 | 提交 admin/123456 → `POST /api/oauth2-auth/login` | auth-server 建立自己的 session（`Set-Cookie: JSESSIONID=...`），302 回 authorize |
| 7 | auth-server | `requireAuthorizationConsent=true` → `302 /api/oauth2-auth/consent?client_id=...&scope=...&state=...` | consent 页 |
| 8 | 用户 | 点"同意授权" → `POST /api/oauth2-auth/authorize` | 生成授权码 code |
| 9 | auth-server | `302 → http://localhost:18090/api/oauth2-client/login/oauth2/code/lab-client?code=<授权码>&state=<同一个state>` | redirect_uri 与第 3 步注册值一致 |
| 10 | client-server | 校验 state（查第 3 步 session，需要 `JSESSIONID_LAB` 一致）→ **服务端间**用 code 换 token（token-uri）→ 调 userinfo（user-info-uri） | 见 3.5 |
| 11 | client-server | 检查绑定：**已绑定** → 正常登录 → `302 http://localhost:13000/#/oauth2`（defaultSuccessUrl）；**未绑定** → 三方用户信息存入 session（属性 `PENDING_OAUTH_BINDING`）→ 抛 `OAuth2BindingRequiredException` → failureHandler `302 http://localhost:13000/#/bind?provider=lab-client` | 绑定页在弹窗里打开 |
| 12 | 弹窗 | 落在绑定页 → `GET /api/oauth2-client/auth/status` + `GET /api/oauth2-client/oauth-pending` | 同 host（localhost），cookie 一致，能读到 pending |
| 13 | 用户 | 选择"注册并绑定 / 登录并绑定 / 绑定到当前账号" → `POST /api/oauth2-client/bind/register | /bind/login | /bind/current` | 成功后删除 `PENDING_OAUTH_BINDING` |
| 14 | 前端 | 绑定成功 → `window.opener.postMessage({type:'oauth2-login-result', status:'success'})` → 主窗口收到后刷新 `/profile` | 见 3.4 |

### 3.3 已绑定用户再次登录

流程同 3.2，但第 11 步走"已绑定"分支 → `302 http://localhost:13000/#/oauth2` → 弹窗里 OAuth2Auth.vue 检测 `window.opener` 存在（isPopup）→ `notifyOpenerAndClose({status:'success'})` → 主窗口刷新。

### 3.4 弹窗 postMessage 通信（跨窗口）

- 主窗口 `OAuth2Auth.vue` 监听 `window` 的 `message` 事件；
- 校验 `event.origin` ∈ { `window.location.origin`（主窗口自己的源）, `VITE_AUTH_SERVER_ORIGIN` }，不通过直接丢弃；
- 绑定页成功 → `window.opener.postMessage({type:'oauth2-login-result', status:'success'}, window.location.origin)`；
- 主窗口收到 → `checkAuthState()` → 刷新登录态。

### 3.5 服务端内部调用（不经过浏览器）

| 调用方 | 目标 | 本地 URL | 说明 |
|---|---|---|---|
| client-server | token-uri | `http://localhost:18090/api/oauth2-auth/token` | 用 code 换 token |
| client-server | user-info-uri | `http://localhost:18090/api/oauth2-auth/userinfo` | 拿三方用户信息 |
| client-server | jwk-set-uri | `http://localhost:18090/api/oauth2-auth/jwks` | 一般不需要 |
| resource-server | jwks（由 issuer-uri 推导发现） | `http://localhost:18090/api/oauth2-auth/jwks` | 验 JWT 签名 |
| resource-server | /api/oauth2-resource/items | 被 client-server 调用（`demo.resource-server.list-uri`） | 带 `Authorization: Bearer <access_token>` |

> 以上这些调用在"容器/服务器"环境下必须用**服务端可达的地址**（见 6.2、6.4）。

### 3.6 客户端注册（OAuth2 Client 管理）

- **存储**：客户端注册数据保存在 auth-server 数据库的 `oauth2_registered_client` 表（建表语句在 `sql/init-oauth2.sql`），**不再硬编码在 `AuthorizationServerConfig` 里**。内置 `lab-client` 由该 SQL 初始化。
- **初始化方式**：建表与种子数据统一在 `sql/init-oauth2.sql`（幂等：`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`），**手动执行**。新库：直接执行整个文件。旧库：重跑一遍即可补齐缺失行（角色、admin/user、`user_role` 映射、`lab-client`），不会覆盖已有数据。**旧库缺角色映射（表现为登录后访问管理页 403）重跑该文件即可修复**。
- **管理页面**：`http://<网关>/api/oauth2-auth/clients`（需以 `admin/123456` 登录，要求 ROLE_ADMIN）。可查看列表、**注册 / 编辑 / 删除**客户端（编辑时 client_secret 留空表示保持原密钥）。
    - 注册表单字段：client_id / client_secret（明文，服务端 BCrypt 后入库）/ 回调地址 / scope / 授权类型 / 客户端认证方式 / 是否要求授权确认 / Token 有效期等。
    - 回调地址默认预填 `app.oauth2.redirect-uri`（即 `@oauth2.base-url@/api/oauth2-client/login/oauth2/code/lab-client`）。
- **联动关系**：client-server 里每个 `spring.security.oauth2.client.registration.*` 必须在 auth-server 的这张表里有**完全一致**的 `client_id / client_secret / redirect_uri` 记录，否则 token 端点报 `invalid_client` / 回调报 `redirect_uri` 不匹配。
- **换环境注意**：seed 的 `lab-client` 行里的 `redirect_uris` 是本地默认值（localhost:18090）。Docker / 服务器 / 生产环境请到管理页删除重建，或用 SQL 更新该行。

---

## 4. 前端页面、路由与环境变量

### 4.1 路由（`frontend/src/views/auth-lab/router.js`）

| 路径 | 别名 | 组件 | 谁跳进来 |
|---|---|---|---|
| `/auth-lab/oauth2` | `/oauth2`、`/login` | OAuth2Auth.vue（登录页/已登录页） | 后端 defaultSuccessUrl、错误重定向、loginPage 都指向 `/#/oauth2` 或 `/#/login` |
| `/auth-lab/bind` | `/bind` | BindOAuth.vue（绑定页） | 后端 failureHandler 指向 `/#/bind?provider=...` |
| `/auth-lab/register` | — | LoginPage.vue（注册页） | 前端内部链接 |

> 后端重定向是**硬编码**的 `/#/bind`、`/#/oauth2`、`/#/login`，所以前端必须注册这些别名（否则页面空白）。**hash 路由**意味着：不管 nginx 用 `/app/` 还是 `/` 承载 SPA，`/#/bind` 都能命中，不需要改后端。
> 后端重定向是**硬编码**的 `/#/bind`、`/#/oauth2`、`/#/login`，所以前端必须注册这些别名（否则页面空白）。**hash 路由**意味着：不管 nginx 用 `/study/` 还是 `/` 承载 SPA，`/#/bind` 都能命中，不需要改后端。

### 4.2 前端环境变量（`frontend/.env.development` / `.env.production`）

| 变量 | development | production | 作用 |
|---|---|---|---|
| `VITE_APP_BASE` | `/` | `/app/` | 静态资源 base（打包后 nginx 放 `/app/` 下） |
| `VITE_APP_BASE` | `/` | `/study/` | 静态资源 base（打包后 nginx 放 `/study/` 下） |
| `VITE_DEV_PORT` | 13000 | — | vite dev 端口 |
| `VITE_API_CLIENT` | `/api/oauth2-client` | 同左 | client-server API 前缀（相对路径，走 vite 代理/nginx 反代） |
| `VITE_API_AUTH` | `/api/oauth2-auth` | 同左 | auth-server API 前缀 |
| `VITE_API_RESOURCE` | `/api/oauth2-resource` | 同左 | resource-server API 前缀 |
| `VITE_OAUTH2_AUTHORIZATION` | `/api/oauth2-client/authorization/` | 同左 | 弹窗入口（`window.open(该值 + provider)`） |
| `VITE_LOGOUT` | `/api/oauth2-client/logout` | 同左 | 登出 |
| `VITE_AUTH_SERVER_ORIGIN` | `http://localhost:18090` | `http://192.168.96.129:14000`（⚠️ 部署时需改） | postMessage 来源白名单 |

> 前端 API 全部是**相对路径**，开发走 vite proxy、生产走 nginx 反代，**同源**，不需要 CORS。唯一跨源的是 OAuth2 流程中后端 302 出来的授权页/回调地址——它们由 `oauth2.base-url` 决定（见下）。

---

## 5. 配置项总览（每个配置在哪、影响什么）

### 5.1 Maven profile（`backend/java/pom.xml`）—— 打包时写死进 jar

| 属性 | desktop | laptop | prod | 作用 |
|---|---|---|---|---|
| `oauth2.base-url` | `http://localhost:18090` | `http://localhost:18090` | `CHANGE_ME` | **所有浏览器可见 + 服务端间 OAuth2 URL 的基础** |
| `nacos.host/port` | 192.168.96.129:28848 | 192.168.6.128:28848 | CHANGE_ME | 注册中心 |
| `nacos.ip` | 192.168.3.4 | 192.168.1.229 | CHANGE_ME | 服务注册到 Nacos 的 IP |
| `mysql.*` | 192.168.96.129:3306 | 192.168.6.128:3306 | CHANGE_ME | 数据库 |
| `redis.*` | 192.168.96.129:6379 | 192.168.6.128:6379 | CHANGE_ME | auth-server session |

打包时 `@oauth2.base-url@` 会被替换进各服务的 `application-{profile}.yml`：

- **client-server**：provider 的 `authorization-uri / token-uri / user-info-uri / jwk-set-uri`、三个注册项的 `redirect-uri`；
- **auth-server**：`spring.security.oauth2.authorizationserver.issuer`、`app.oauth2.redirect-uri`；
- **resource-server**：`spring.security.oauth2.resourceserver.jwt.issuer-uri`。

> 运行时可用环境变量覆盖任意配置（Spring Boot 松散绑定），例如 `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_TOKEN-URI`。这解决 Docker/服务器下"服务端间地址"问题（见 6.2）。

### 5.2 client-server 单独配置

| 配置 | 位置 | 当前值 | 作用 |
|---|---|---|---|
| `app.frontend-base-url` | `application-{profile}.yml` | `http://localhost:13000` | 后端成功/失败后**重定向回前端**的地址（`/#/oauth2`、`/#/bind` 等） |
| `server.servlet.session.cookie.name` | `application.yml` | `JSESSIONID_LAB` | client-server 会话 Cookie（与小程序 request.js 对齐） |
| `server.forward-headers-strategy` | `application-{profile}.yml` | `NATIVE` | 让后端根据 `X-Forwarded-*` 还原真实 URL（nginx 反代时必须） |

### 5.3 nginx（`nginx/nginx.conf`）

| 配置 | 当前值 | 说明 |
|---|---|---|
| `location /app/` | `try_files ... /app/index.html` | SPA 静态资源 + hash 路由兜底 |
| `location /study/` | `try_files ... /study/index.html` | SPA 静态资源 + hash 路由兜底 |
| `location /api/` | `proxy_pass http://192.168.96.129:18090` | 前端同源反代到网关（**部署时改成实际网关地址**） |
| `location /api/poker/` | 同上 + websocket | 优先匹配 |

### 5.4 Docker（`backend/java/docker-compose.yml`）

端口映射：gateway `18090:18090`、auth-server `18085:18085`、resource-server `18086:18086`、client-server `18087:18087`（容器内端口 = 宿主机端口）。镜像基于 `backend/java/build/jar/*.jar`，**profile 在打包时已定**。

---

## 6. 四种部署模式详解

> 通用判断口诀：**浏览器看到的地址要能被浏览器访问，后端进程访问的地址要能被后端进程访问，且整个 OAuth2 流程里 `JSESSIONID_LAB` 始终待在同一个 Host 上。**

### 6.1 模式一：全部本地 IDE 启动（前端 Vite + 后端 IDE）

```
浏览器
  │  ① http://localhost:13000/#/auth-lab/oauth2（vite dev）
  │  ② /api/*  →  vite 代理  →  http://localhost:18090（网关）
  ▼
localhost:18090 网关（IDE 启动）
  ├── /api/oauth2-auth/**    → auth-server   :18085
  ├── /api/oauth2-resource/**→ resource-server:18086
  └── /api/oauth2-client/**  → client-server  :18087
基础设施：Nacos/MySQL/Redis 在 192.168.96.129（desktop）或 192.168.6.128（laptop）
```

- 打包/启动用 profile：`desktop`（默认）或 `laptop`。
- `oauth2.base-url = http://localhost:18090`，`app.frontend-base-url = http://localhost:13000`。
- **整个流程全部在 localhost 上完成**：授权页、consent、回调、绑定页、`JSESSIONID_LAB` 全部同一个 Host → Cookie 一致 → 能跑通。
- 常见坑：
    1. 本机 IP 与 pom 里 `nacos.ip`（192.168.3.4 / 192.168.1.229）不一致 → 服务注册到 Nacos 的 IP 不对 → 网关 `lb://` 找不到服务。改成自己机器实际 IP。
    2. MySQL/Redis/Nacos 连不上 → 检查 profile 里基础设施地址。
    3. 直接访问 `/#/bind` 看到"绑定会话已过期"是**正常**的：没走 OAuth 流程就没有 pending 会话；走完第 3 章流程才有。

### 6.2 模式二：后端 Docker Desktop + 前端 IDE（和模式一很像，但有个大坑）

```
浏览器
  │  ① http://localhost:13000（vite dev，还开着）
  │  ② /api/*  →  vite 代理  →  http://localhost:18090（= 容器映射到宿主机的端口）
  ▼
Docker Desktop 里：
  security-gateway           容器 :18090 ← 宿主 18090
  security-oauth2-auth-server 容器 :18085 ← 宿主 18085
  security-oauth2-resource-server 容器 :18086 ← 宿主 18086
  security-oauth2-client      容器 :18087 ← 宿主 18087
```

- **浏览器视角和模式一完全一样**：`localhost:13000` + `localhost:18090`，`oauth2.base-url` 保持 `http://localhost:18090` 即可，页面跳转、Cookie 都没问题。
- **大坑：容器内部访问 `localhost:18090` 会连到容器自己**（每个容器有自己的网络命名空间），而不是宿主机网关。于是：

| 调用 | 在容器里访问 localhost:18090 的结果 |
|---|---|
| client-server → token-uri `http://localhost:18090/api/oauth2-auth/token` | ❌ Connection refused（容器里 18090 没有服务） |
| client-server → user-info-uri | ❌ 同上 |
| resource-server 按 issuer-uri 发现 jwks | ❌ 同上 |

- **解法：用运行时环境变量只覆盖"服务端间调用"的地址**，浏览器可见的 URL 一律不动：

  ```bash
  # 方式 A：走宿主机网关（Docker Desktop 自带 host.docker.internal 指向宿主机）
  SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_TOKEN-URI=http://host.docker.internal:18090/api/oauth2-auth/token
  SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_USER-INFO-URI=http://host.docker.internal:18090/api/oauth2-auth/userinfo
  SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_JWK-SET-URI=http://host.docker.internal:18090/api/oauth2-auth/jwks

  # 资源服务器：保留 issuer-uri（用于校验 iss=localhost:18090），单独指 jwk-set-uri
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK-SET-URI=http://host.docker.internal:18090/api/oauth2-auth/jwks

  # 方式 B（更干净）：docker compose 网络里直接用服务名 + 端口，绕过网关
  # SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_TOKEN-URI=http://security-oauth2-auth-server:18085/api/oauth2-auth/token
  # SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_USER-INFO-URI=http://security-oauth2-auth-server:18085/api/oauth2-auth/userinfo
  # SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LAB-AUTH-SERVER_JWK-SET-URI=http://security-oauth2-auth-server:18085/api/oauth2-auth/jwks
  ```

- Nacos：容器注册到 Nacos 的 IP 要保证**网关容器能访问**。最简单是让 `nacos.ip` 填宿主机 LAN IP（容器通常能访问宿主 LAN IP）；或用 compose 服务名/网络别名。若网关容器到业务容器不通，`lb://` 转发会 503。
- 打包：`./script/build-backend.sh --profile desktop`（或 laptop）打包 jar → `cd backend/java && docker compose up -d --build`。prod 环境变量按模式四覆盖。

### 6.3 模式三：前端用 Nginx 部署（后端仍本地/Docker）

```
浏览器 → http://<nginx-host>/app/（SPA 静态文件）
浏览器 → http://<nginx-host>/study/（SPA 静态文件）
                │
                ├── /app/*           → nginx 本地静态文件（npm run build 产物）
                ├── /study/*           → nginx 本地静态文件（npm run build 产物）
                └── /api/*           → nginx 反代 → 网关 :18090 → 各微服务
```

- 前端构建：`npm run build`（`.env.production`，`VITE_APP_BASE=/app/`）→ 产物复制到 nginx `/usr/share/nginx/html/app/`（`build/dist`）。
- 前端构建：`npm run build`（`.env.production`，`VITE_APP_BASE=/study/`）→ 产物复制到 nginx `/usr/share/nginx/html/study/`（`build/dist`）。
- **必须改的配置**：
    1. `nginx/nginx.conf` 的 `location /api/` `proxy_pass` 目标改成实际网关地址（现在是 `192.168.96.129:18090`；网关在本机就改成 `http://localhost:18090` 或 `http://127.0.0.1:18090`）。
    2. client-server 的 `app.frontend-base-url` 改成 nginx 地址：`http://<nginx-host>/app`（例如 `http://localhost/app`）。否则后端重定向到 `localhost:13000`（vite）→ 404。
    2. client-server 的 `app.frontend-base-url` 改成 nginx 地址：`http://<nginx-host>/study`（例如 `http://localhost/study`）。否则后端重定向到 `localhost:13000`（vite）→ 404。
    3. 前端重新构建、后端重新打包（`frontend-base-url` 在 jar 里）。
- **不用改的**：
    - `oauth2.base-url` 仍是 `http://localhost:18090`（浏览器与网关同机时）——授权页/回调 URL 由它生成；
    - 前端 API 相对路径 `/api/*` 由 nginx 反代，同源，无 CORS。
- 为什么 `/#/bind` 还能用：hash 路由，`frontend-base-url + "/#/bind"` = `http://localhost/app/#/bind`，nginx `location /app/` 返回 index.html，Vue Router 解析 `#/bind` 命中别名。**base `/app/` 只影响静态资源路径，不影响 hash 路由**。
- 为什么 `/#/bind` 还能用：hash 路由，`frontend-base-url + "/#/bind"` = `http://localhost/study/#/bind`，nginx `location /study/` 返回 index.html，Vue Router 解析 `#/bind` 命中别名。**base `/study/` 只影响静态资源路径，不影响 hash 路由**。
- nginx 必须带 `X-Forwarded-*` 头（nginx.conf 已带 `X-Forwarded-Proto/Host/For`），后端 `forward-headers-strategy: NATIVE` 才能还原真实 URL。

### 6.4 模式四：后端在一台服务器，前端（nginx）在另一台服务器

这是最容易翻车的模式：**localhost 彻底失效**，必须引入"公网地址"。

#### 推荐架构：单源（前端 nginx 统一入口，后端只走 /api 反代）

```
浏览器（任意地方）
   │  访问 https://app.example.com/app/（前端 nginx）
   │  访问 https://app.example.com/study/（前端 nginx）
   │  整个 OAuth2 流程的所有跳转都发生在 https://app.example.com 这一个源上
   ▼
前端服务器 nginx
   ├── /app/*   → SPA 静态文件
   ├── /study/*   → SPA 静态文件
   └── /api/*   → 反代 → 后端网关 http://<后端内网IP>:18090（或经 VPN/公网）
后端服务器
   gateway:18090 → auth-server / resource-server / client-server
```

配置：

| 配置 | 值 |
|---|---|
| `oauth2.base-url`（打包时） | `https://app.example.com`（**不要带 /app**，因为端点路径是 `/api/oauth2-auth/...`） |
| `app.frontend-base-url`（client-server） | `https://app.example.com/app` |
| `oauth2.base-url`（打包时） | `https://app.example.com`（**不要带 /study**，因为端点路径是 `/api/oauth2-auth/...`） |
| `app.frontend-base-url`（client-server） | `https://app.example.com/study` |
| `.env.production` `VITE_AUTH_SERVER_ORIGIN` | `https://app.example.com` |
| nginx `location /api/` proxy_pass | 后端网关（内网/公网地址） |
| prod profile 的 mysql/redis/nacos | 后端服务器可达的地址 |

流程（全部同源 `https://app.example.com`）：

1. 弹窗打开 `https://app.example.com/api/oauth2-client/authorization/lab-client` → nginx → 网关 → client-server；
2. client-server 下发 `JSESSIONID_LAB`（host=app.example.com）→ `302 https://app.example.com/api/oauth2-auth/authorize?...` → nginx → 网关 → auth-server；
3. 登录/consent → `302 https://app.example.com/api/oauth2-client/login/oauth2/code/lab-client?code=...&state=...` → nginx → 网关 → client-server（**cookie 还在**）；
4. 未绑定 → `302 https://app.example.com/app/#/bind?provider=...` → nginx 返回 SPA → 绑定页 `GET /api/oauth2-client/oauth-pending` 同源 → 一切正常。
4. 未绑定 → `302 https://app.example.com/study/#/bind?provider=...` → nginx 返回 SPA → 绑定页 `GET /api/oauth2-client/oauth-pending` 同源 → 一切正常。

服务端间调用（token/userinfo/jwks）此时走 `https://app.example.com/api/oauth2-auth/...`——**如果后端服务器能访问到 app.example.com（公网回环）也行**，但更稳妥的是用环境变量把这三项指到内网网关/容器名（同 6.2 的覆盖方式）。

#### 不推荐的架构：前后端不同域名（跨源）

若前端 `http://frontend.example.com`、后端 `http://backend.example.com:18090`，且 `oauth2.base-url = http://backend.example.com:18090`：

1. 弹窗在 frontend.example.com 发起 → client-server 下发 `JSESSIONID_LAB`（host=frontend.example.com）→ 302 到 backend.example.com:18090；
2. 浏览器跳到 backend.example.com —— **JSESSIONID_LAB 不带** → 回调时 `authorization_request_not_found`；
3. SecurityConfig 的失败处理器会自动重试一次（`/api/oauth2-client/authorization/lab-client?retry=1`，相对路径落在 backend.example.com）→ 第二次授权在 backend.example.com 建新 session → 回调能过 → 但最终 `302 frontend.example.com/app/#/bind`；
3. SecurityConfig 的失败处理器会自动重试一次（`/api/oauth2-client/authorization/lab-client?retry=1`，相对路径落在 backend.example.com）→ 第二次授权在 backend.example.com 建新 session → 回调能过 → 但最终 `302 frontend.example.com/study/#/bind`；
4. 绑定页 `oauth-pending` 走 frontend.example.com → 读到的是**第一次**（空）session → **"绑定会话已过期"**。

> 结论：跨源部署下绑定流程不可用（已登录用户的"再次登录"能碰巧过，因为第二次重试建了 session）。要么改回单源，要么接受限制。

#### 生产环境 checklist

- [ ] `backend/java/pom.xml` 的 `prod` profile：`oauth2.base-url`、`nacos.*`、`mysql.*`、`redis.*` 全部填真实值；
- [ ] client-server `app.frontend-base-url` = 前端公网地址（+`/app`）；
- [ ] client-server `app.frontend-base-url` = 前端公网地址（+`/study`）；
- [ ] 前端 `npm run build` 用 `.env.production`，`VITE_AUTH_SERVER_ORIGIN` 改成前端公网源；
- [ ] nginx `location /api/` 反代到后端网关；
- [ ] 服务端间调用用环境变量覆盖到内网地址（可选但推荐）；
- [ ] 防火墙/安全组放行浏览器需要直连的端口（单源架构下只需放行 80/443；网关端口可只对内网/nginx 开放）。

---

## 7. 常见问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 打开 `/#/bind` 页面空白 | 前端路由没有 `/bind`（或 `/oauth2`、`/login`）别名；或前端没重新构建/缓存 | 确认 `router.js` 有别名；重新 `npm run dev` 或 `npm run build`；强刷浏览器 |
| 绑定页显示"绑定会话已过期" | 直接访问绑定页（没走 OAuth 流程）→ 正常；或跨 Host 导致 cookie 分裂 | 走完整流程；检查 `oauth2.base-url` 与浏览器访问地址是否同源 |
| 控制台报 `authorization_request_not_found` | client-server 的 session 丢了（跨 Host 不带 cookie、服务重启、session 过期） | 保证整个流程同一个 Host；看失败处理器是否触发重试；日志查 DEBUG |
| 报 `redirect_uri` 不匹配 | 三处 redirect-uri 不一致（auth-server 注册 / client-server registration / 实际地址） | 统一 `oauth2.base-url`，localhost vs 127.0.0.1 区分清楚 |
| 回调后 401 / `iss` 校验失败 | issuer、issuer-uri、oauth2.base-url 三者不一致；或 resource-server 拉不到 JWKS | 统一 issuer 基础地址；容器环境按 6.2 覆盖 jwk-set-uri |
| 容器里"连接 localhost:18090 被拒" | 服务端间调用地址在容器内不可达 | 用 `host.docker.internal` 或 compose 服务名覆盖 token/userinfo/jwks（6.2） |
| 网关转发 503 | Nacos 里服务注册 IP 不可达 | 检查 `nacos.ip`，保证网关容器能访问 |
| 生产 nginx 下后端重定向到 vite 端口 | `app.frontend-base-url` 没改成 nginx 地址 | 6.3 |

---

### 7.1 登录后地址栏出现 `/continue` 或 `?continue`

**这是什么**：不是本项目自己的页面/路由，也不是 Spring Authorization Server 生成的。它是 **Spring Security 6 的 `HttpSessionRequestCache` 续跳标记**：

1. 你访问了一个需要登录的地址 A（如 `/api/oauth2-auth/clients` 或 `/api/oauth2-auth/authorize?...`）；
2. Spring Security 把 A 存进 session，302 到登录页 `/api/oauth2-auth/login`；
3. 登录成功后，默认 success handler 302 回 A，并附一个**空的 `?continue`** 参数（标记"这次请求要恢复 session 里存的原始请求"）；
4. `RequestCacheAwareFilter` 看到 `?continue` 后，把当前请求"回放"成原始请求 A。

所以地址栏出现 `.../clients?continue` 或 `.../authorize?...&continue` 是 Spring Security 的**预期行为**，回放后等价于直接访问 A。

**本项目已做的处理**：auth-server 改用自定义 `AuthServerLoginSuccessHandler`，登录成功后直接 302 回 A（去掉 `?continue`），避免：

- 地址栏出现让人困惑的 `/continue`；
- `continue` 空参数混进 OAuth2 `/authorize` 请求，被 Spring Authorization Server 1.3 存进授权上下文，导致令牌交换等环节报错（spring-authorization-server#1333 同类问题）。

**如果登录后真的停在 `/continue` 且页面空白/404**，按顺序检查：

1. 浏览器地址栏完整 URL 是什么？
    - 若是 `.../authorize?response_type=code&...` → 走的是 OAuth2 授权续跳，看页面报错内容（多数是 `oauth2_registered_client` 表里 `lab-client` 的 `redirect_uris` / `scope` 与 client-server 配置不一致）。
    - 若是 `/`（没有 saved-request，比如直接打开登录页登录）→ 现在会落到客户端管理页（`defaultSuccessUrl`），不再 404。
    - 若真是 `/continue` 这个路径 → 大概率是浏览器残留了其它应用的会话/书签，清掉该 Host 的 Cookie 和历史再试。
2. 确认 `oauth2_registered_client` 表里 `lab-client` 的 `redirect_uris` 与 client-server 的 `redirect-uri` 逐字符一致。
3. 确认浏览器能拿到 auth-server 的 `JSESSIONID` Cookie（同一 Host：`localhost` 与 `127.0.0.1` 是不同 Host）。

### 7.2 auth-server 独立使用（不经过前端）

auth-server 自带一套独立的登录 + 客户端管理页面，与前端/OAuth2 三方流程解耦：

- 入口：`http://<网关>/api/oauth2-auth/clients`（根路径 `/`、`/api/oauth2-auth` 会自动重定向过去；未登录先跳登录页）
- 账号：`admin / 123456`（ROLE_ADMIN）；普通 `user` 只能登录，访问管理页会 403
- 登录成功：若从受保护页面（/clients、/authorize 等）跳去登录 → 回到原页面；直接打开登录页登录 → 进入客户端管理页
- 该登录只影响 auth-server 自己的 session（`JSESSIONID`），与前端/client-server 的 `JSESSIONID_LAB` 互不影响

---

## 8. 配置速查表（按部署模式）

| 配置项 | 模式一：全本地 IDE | 模式二：后端 Docker + 前端 IDE | 模式三：前端 nginx | 模式四：前后端异机 |
|---|---|---|---|---|
| 前端启动 | `npm run dev` :13000 | `npm run dev` :13000 | `npm run build` → nginx `/app/` | `npm run build` → nginx `/app/` |
| 前端启动 | `npm run dev` :13000 | `npm run dev` :13000 | `npm run build` → nginx `/study/` | `npm run build` → nginx `/study/` |
| `oauth2.base-url`（打包） | `http://localhost:18090` | `http://localhost:18090` | `http://localhost:18090` | 前端公网源，如 `https://app.example.com` |
| `app.frontend-base-url` | `http://localhost:13000` | `http://localhost:13000` | `http://<nginx>/app` | `https://app.example.com/app` |
| `app.frontend-base-url` | `http://localhost:13000` | `http://localhost:13000` | `http://<nginx>/study` | `https://app.example.com/study` |
| nginx `/api` 反代 | 不用 nginx | 不用 nginx | 网关地址（本机 localhost 或 VM IP） | 后端网关地址 |
| 服务端间 token/userinfo/jwks | 默认（localhost:18090） | **必须覆盖**：host.docker.internal 或容器名 | 默认 | 建议覆盖为内网地址 |
| `VITE_AUTH_SERVER_ORIGIN` | `http://localhost:18090` | `http://localhost:18090` | 前端源 | 前端源 |
| Nacos IP（`nacos.ip`） | 自己机器实际 IP | 网关容器可访问的 IP | 网关容器可访问的 IP | 后端服务器实际 IP |
| prod profile | 不用 | 不用（desktop/laptop 打包） | 不用 | **必须填全**（prod） |

> 记住：**浏览器视角 = `oauth2.base-url`（不带 /app）+ 前端 `frontend-base-url`（带 /app）；服务端视角 = 容器/内网可达地址（环境变量覆盖）。** 两者不要混。
> 记住：**浏览器视角 = `oauth2.base-url`（不带 /study）+ 前端 `frontend-base-url`（带 /study）；服务端视角 = 容器/内网可达地址（环境变量覆盖）。** 两者不要混。

