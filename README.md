# Auth Lab

## 简介 / Introduction

**中文** — 从 Study Hub 抽离的 Spring Security 认证实验室。6 个认证服务（Session / JWT / Opaque Token / OAuth2 四大认证范式）直连端口独立运行，前端 Vite 代理按路径分发；配合 Vue 3 演示前端，并行对照四种认证的差异与适用场景。

**English** — A Spring Security authentication laboratory split from Study Hub. Six auth services (Session, JWT, Opaque Token, and OAuth2) run independently on direct ports, routed by the frontend's Vite proxy; paired with a Vue 3 demo frontend to compare all four auth paradigms side by side.

## 目录结构

```text
auth-lab/
├── backend/     # 6 个认证服务（直连端口，无网关/Nacos）
└── frontend/    # Vue 3 演示前端（vite 代理按路径分发）
```

## 前置依赖

- JDK 21、Maven
- MySQL、Redis（本地 dev 默认见 `backend/pom.xml` 的 `dev` profile）
- OAuth2 初始化 SQL：`backend/sql/init-oauth2.sql`

## 后端

```powershell
cd backend
mvn spring-boot:run -pl session-auth,jwt-auth,opaque-auth,oauth2-auth/auth-server,oauth2-auth/client-server,oauth2-auth/resource-server -am
```

或分别启动各模块。各服务直连端口：session 18082 / jwt 18083 / opaque 18088 / auth-server 18085 / resource-server 18086 / client-server 18087。

详细文档见 [backend/docs/](backend/docs/)。

## 前端

Vue 3 + Vue Router 4 + Vite 5 的单页演示应用：**无状态管理库、无 UI 组件库**，四个认证 Demo 共用一套手写基础组件与设计 token，把差异集中暴露在各自页面里。

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开 <http://127.0.0.1:13008/#/auth-lab/session>。

### 页面与路由

hash 模式，全部挂在 `/auth-lab` 下：

| 路径 | 别名 | 内容 |
|---|---|---|
| `#/auth-lab/session` | — | Session 认证（Cookie + Redis） |
| `#/auth-lab/jwt` | — | JWT 认证（无状态、不可撤销） |
| `#/auth-lab/opaque` | — | Opaque Token 认证（Redis 查证、可撤销） |
| `#/auth-lab/oauth2` | `#/oauth2`、`#/login` | OAuth2 主入口 / 弹窗回跳落点 |
| `#/auth-lab/register` | — | 本地账号注册 |
| `#/auth-lab/bind` | `#/bind` | 三方账号绑定（弹窗内） |

后三个别名与后端 Security 配置的 `redirect_uri` / `defaultSuccessUrl` 一一对应，改动需前后端同步。

### 请求分发

前端只访问自身源 `localhost:13008`，由 Vite 代理按路径前缀转发到各服务直连端口（生产环境换成 Nginx 反代同样规则）；关键点：本地 `oauth2.base-url` 就指向前端源，所以 client-server 换 token、resource-server 发现 JWKS 这些**服务端之间**的调用同样走这张代理表。

| 前缀 | 目标 | 前缀 | 目标 |
|---|---|---|---|
| `/api/session` | 18082 | `/api/oauth2-client` | 18087 |
| `/api/jwt` | 18083 | `/api/oauth2-auth` | 18085 |
| `/api/opaque` | 18088 | `/api/oauth2-resource` | 18086 |
| — | — | `/.well-known` | 18085 |

### 更多

目录结构、环境变量（`.env.development`）、四类认证的差异对照表、组件清单与排查提示见 **[frontend/README.md](frontend/README.md)**。

## 来源

原位于 Study Hub 的 `spring-security-auth-lab`，现独立维护于 [auth-lab](https://github.com/woyaoxuexi1231/auth-lab)。
