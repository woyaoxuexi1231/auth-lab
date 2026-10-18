# Auth Lab — 前端

Auth Lab 的演示前端：一个 Vue 3 单页应用，把后端 6 个认证服务的四类认证范式（Session / JWT / Opaque Token / OAuth2）放在同一套 UI 里并行对照。

- **无状态管理库**（不用 Pinia/Vuex）：每个 Demo 页自己持有登录态，因为"状态怎么存"本身就是这四个 Demo 的对比点。
- **无 UI 组件库**：`src/components/` 下 7 个手写基础组件 + `src/styles/` 的设计 token。
- **无网关**：浏览器只访问前端源（默认 `localhost:13008`），由 Vite 代理按路径前缀把请求分发给各服务直连端口；生产环境由 Nginx 承担同样的转发角色。

## 快速开始

```powershell
npm install
npm run dev     # http://127.0.0.1:13008
```

打开 <http://127.0.0.1:13008/#/auth-lab/session>。

后端未启动时页面能正常渲染，只是调用接口会返回网络错误 —— 每个 Demo 都把原始响应/错误显示在 `CodeBlock` 里，方便确认代理是否打通。

其它命令：

```powershell
npm run build     # 产物输出到 dist/（已 gitignore）
npm run preview   # 本地预览构建产物
```

## 目录结构

```text
frontend/
├── index.html                 # 唯一 HTML 入口（SPA 挂载点 #app）
├── vite.config.js             # dev server 端口 + 按路径前缀的代理表
├── .env.development           # base / 端口 / 各服务 API 前缀
└── src/
    ├── main.js                # createApp + router；样式按 tokens → base 引入
    ├── App.vue                # 只做一件事：AppLayout 包 <router-view/>
    ├── router/index.js        # hash 路由表（全部挂在 /auth-lab 下）
    ├── layouts/AppLayout.vue  # 全局顶栏（品牌 + 四个 Demo 切换）
    ├── api/                   # 按后端服务拆分的请求层
    │   ├── http.js            # 统一 fetch 封装（credentials:'include'、form/JSON、raw 模式）
    │   ├── authSession.js     # /api/session
    │   ├── authJwt.js         # /api/jwt
    │   ├── authOpaque.js      # /api/opaque
    │   └── oauth2.js          # /api/oauth2-client（含 provider 列表与头像占位）
    ├── components/            # 7 个基础展示组件
    ├── styles/                # tokens.css（设计变量）+ base.css（reset/排版）
    └── views/auth-lab/
        ├── AuthLabLayout.vue  # 四个 Demo 的二级切换导航 + 子路由出口
        ├── demos/             # Session / Jwt / Opaque 三个单页 Demo
        └── oauth2/            # OAuth2Auth（主入口）、LoginPage（注册）、BindOAuth（绑定）
```

## 路由表

hash 模式（`createWebHashHistory`），base 取自 `VITE_APP_BASE`。

| 路径 | 别名 | 视图 | 说明 |
|---|---|---|---|
| `/` | — | — | 重定向到 `/auth-lab/session` |
| `/auth-lab` | — | `AuthLabLayout` | 重定向到 session |
| `/auth-lab/session` | — | `demos/SessionDemo` | Cookie + Redis Session |
| `/auth-lab/jwt` | — | `demos/JwtDemo` | 无状态 JWT |
| `/auth-lab/opaque` | — | `demos/OpaqueDemo` | 不透明令牌（Redis 查证） |
| `/auth-lab/oauth2` | `/oauth2`、`/login` | `oauth2/OAuth2Auth` | OAuth2 主入口（也承载弹窗回跳） |
| `/auth-lab/register` | — | `oauth2/LoginPage` | 本地账号注册 |
| `/auth-lab/bind` | `/bind` | `oauth2/BindOAuth` | 三方账号绑定（弹窗内） |

`/oauth2`、`/login`、`/bind` 三个别名与后端 Security 配置的 `redirect_uri` / `defaultSuccessUrl` 一一对应。**改动这些别名必须同步改后端配置**，否则 OAuth2 回跳会 404。

## 环境变量

全部定义在 `.env.development`（本项目暂无 production 变量文件，构建时用默认值）。

| 变量 | 默认值 | 用途 |
|---|---|---|
| `VITE_APP_BASE` | `/` | 静态资源 base，同时作为 router base；部署到子路径（如 `/app/`）时改这里 |
| `VITE_DEV_PORT` | `13008` | dev server 端口，需与后端 `oauth2.base-url` 的端口一致 |
| `VITE_API_SESSION` | `/api/session` | session-auth 前缀 |
| `VITE_API_JWT` | `/api/jwt` | jwt-auth 前缀 |
| `VITE_API_OPAQUE` | `/api/opaque` | opaque-auth 前缀 |
| `VITE_API_CLIENT` | `/api/oauth2-client` | client-server 前缀 |
| `VITE_API_AUTH` | `/api/oauth2-auth` | auth-server 前缀（前端 JS 不直接调，见下方说明） |
| `VITE_API_RESOURCE` | `/api/oauth2-resource` | resource-server 前缀（同上） |
| `VITE_OAUTH2_AUTHORIZATION` | `/api/oauth2-client/authorization/` | 三方登录弹窗入口 |
| `VITE_LOGOUT` | `/api/oauth2-client/logout` | OAuth2 登出 |
| `VITE_AUTH_SERVER_ORIGIN` | `http://localhost:13008` | postMessage 来源白名单的额外允许源 |

> `VITE_API_AUTH` / `VITE_API_RESOURCE` 不被任何 `src/api/*.js` 引用 —— 它们的调用方是**后端服务自身**（client-server 用 code 换 token、resource-server 发现 JWKS），因为本地 `oauth2.base-url` 就指向前端源，这些服务端请求同样经过 Vite 代理。所以代理表里必须有这两条，但前端代码里搜不到它们。

## 请求链路（开发环境）

`vite.config.js` 的代理把浏览器侧的相对路径转发到各服务直连端口，前端因此始终同源，不需要 CORS：

| 浏览器请求 | 实际到达 |
|---|---|
| `/api/session/**` | session-auth `:18082` |
| `/api/jwt/**` | jwt-auth `:18083` |
| `/api/opaque/**` | opaque-auth `:18088` |
| `/api/oauth2-client/**` | client-server `:18087` |
| `/api/oauth2-auth/**` | auth-server `:18085` |
| `/api/oauth2-resource/**` | resource-server `:18086` |
| `/.well-known/**` | auth-server `:18085` |

OAuth2 完整逐跳流程（含 302 与 postMessage 时序）见 [`../backend/docs/OAuth2认证流程与多环境部署指南.md`](../backend/docs/OAuth2认证流程与多环境部署指南.md)。

## 四个 Demo 的差异点

| | Session | JWT | Opaque Token | OAuth2 |
|---|---|---|---|---|
| 凭据形态 | `JSESSIONID` Cookie | 自包含 JWT | 随机字符串 | 授权码 → 服务端会话 |
| 凭据存哪 | 服务端 Redis，浏览器只存 ID | 浏览器 `localStorage`（`jwt_token`） | 浏览器 `localStorage`（`opaque_token`） | 服务端 Session |
| 谁校验 | 服务端查 Session | 服务端本地验签 | 服务端查 Redis | client-server 校验 |
| 能否撤销 | 能（删 Session） | **不能**（登出仅本地清除） | 能（删 Redis key） | 能 |
| 跨域携带 | 依赖 Cookie 同站策略 | 靠 `Authorization` 头 | 靠 `Authorization` 头 | 302 + Cookie |

`api/http.js` 的 `credentials: 'include'` 是所有请求的默认行为；`raw: true` 用于需要读取原始状态码/错误文案的场景（Session 登录、OAuth2 的 302 流程）。

## 基础组件

| 组件 | 职责 |
|---|---|
| `PageHeader` | 页面标题 + 副标题 |
| `StatusBar` | 登录态条（success / warning / info / error） |
| `SegmentedNav` | 分段切换（Demo 导航、注册/登录 tab） |
| `FormField` | 带 label 的输入框，支持 `@enter` |
| `AppButton` | 主/次/危险按钮，含 loading 态 |
| `AppCard` | 内容卡片容器 |
| `CodeBlock` | 等宽输出区，用于展示响应原文 |

## 排查提示

- **接口 404 / 502**：先确认对应后端服务是否在目标端口启动，再核对 `vite.config.js` 代理目标端口。
- **Cookie 带不上**：Cookie 按 Host（不含端口）归属。始终用 `localhost:13008` 访问，不要混用 `127.0.0.1` 和 `localhost`。
- **OAuth2 弹窗回跳后主窗口没刷新**：`oauth2-login-result` postMessage 的来源白名单只允许 `window.location.origin` 与 `VITE_AUTH_SERVER_ORIGIN`；跨机访问时后者要改成实际访问来源。
- **登录态错乱**：JWT / Opaque 的 token 存在 `localStorage`，与后端 Session 无关。清一下 `localStorage` 再试。
