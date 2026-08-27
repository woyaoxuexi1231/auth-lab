# Auth Lab

## 简介 / Introduction

**中文** — 从 Study Hub 抽离的 Spring Security 认证实验室。Gateway 统一入口编排 6 个认证微服务（Nacos 注册发现），覆盖 Session / JWT / Opaque Token / OAuth2 四大认证范式；配合 Vue 3 演示前端，并行对照四种认证的差异与适用场景。

**English** — A Spring Security authentication laboratory split from Study Hub. A Gateway unifies six auth microservices (service-discovered via Nacos) covering Session, JWT, Opaque Token, and OAuth2, paired with a Vue 3 demo frontend to compare all four auth paradigms side by side.

## 目录结构

```text
auth-lab/
├── backend/     # Gateway + 6 个认证微服务（Nacos）
└── frontend/    # Vue 3 演示前端
```

## 前置依赖

- JDK 21、Maven
- MySQL、Redis、Nacos（本地 dev 默认见 `backend/pom.xml` 的 `dev` profile）
- OAuth2 初始化 SQL：`backend/sql/init-oauth2.sql`

## 后端

```powershell
cd backend
mvn spring-boot:run -pl gateway,session-auth,jwt-auth,opaque-auth,oauth2-auth/auth-server,oauth2-auth/client-server,oauth2-auth/resource-server -am
```

或分别启动各模块。Gateway 统一入口：`http://localhost:18090`

详细文档见 [backend/docs/](backend/docs/)。

## 前端

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开 <http://127.0.0.1:13008/#/auth-lab/session>

OAuth2 回跳别名：`#/oauth2`、`#/login`、`#/bind`（与后端 Security 配置一致）。

## 来源

原位于 Study Hub 的 `spring-security-auth-lab`，现独立维护于 [auth-lab](https://github.com/woyaoxuexi1231/auth-lab)。
