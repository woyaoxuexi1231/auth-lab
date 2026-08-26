# GitHub / Google OAuth2 三方认证接入指南
# GitHub / Google OAuth2 三方登录接入指南

> 目的：把本项目 `client-server` 接入 GitHub / Google，让用户可以用
> 三方账号登录。**开始前务必先读第 0 节**——它决定了后面所有要登记的 URL。

## 概述
---

本项目支持三种 OAuth2 登录方式：
## 0. 前置知识：本项目怎么接入三方登录

1. **Lab 认证服务器**（自建，开箱即用）
2. **GitHub OAuth**（需配置 GitHub OAuth App）
3. **Google OAuth**（需配置 Google Cloud Console）
### 0.1 接入的本质

## 一、GitHub OAuth 配置
接入三方登录 = 三步：

### 1. 创建 GitHub OAuth App
```
① 在平台（GitHub/Google）创建一个应用，拿到两样凭据：Client ID、Client Secret
② 把"回调 URL"登记到平台 —— 平台验证后，授权完成后才会回跳到这里
③ 把凭据填入本项目配置，与平台的授权端点对接
```

1. 登录 GitHub，进入 [Developer Settings > OAuth Apps](https://github.com/settings/developers)
2. 点击 **New OAuth App**
3. 填写以下信息：
    - **Application name**: `OAuth2 Lab Client`（任意名称）
    - **Homepage URL**: `http://localhost:18087`
    - **Authorization callback URL**: `http://localhost:18087/login/oauth2/code/github`
4. 点击 **Register application**
5. 生成 **Client Secret**（点击 Generate a new client secret）
### 0.2 本项目实际使用的回调 URL（重要！）

### 2. 配置 application.yml
回调 URL = `oauth2.base-url` + `/api/oauth2-client/login/oauth2/code/{provider}`

- `oauth2.base-url` 是 Maven 打包时注入的占位符，定义在 `backend/java/pom.xml`，
  本地开发默认 **`http://localhost:18090`**（多环境取值见
  [OAuth2认证流程与多环境部署指南](../OAuth2认证流程与多环境部署指南.md)）
- 路径里的 `/api/oauth2-client` 前缀来自 client-server 的
  `loginProcessingUrl("/api/oauth2-client/login/oauth2/code/*")` 配置

| provider | 回调 URL（本地开发环境）                                              |
|----------|----------------------------------------------------------------|
| github   | `http://localhost:18090/api/oauth2-client/login/oauth2/code/github` |
| google   | `http://localhost:18090/api/oauth2-client/login/oauth2/code/google` |
| lab-client | `http://localhost:18090/api/oauth2-client/login/oauth2/code/lab-client` |

> ⚠ 平台登记的 URL 必须与本项目 `redirect-uri` **完全一致**（协议、主机、
> 端口、路径一个字符都不能差）。`localhost` 和 `127.0.0.1` 视为不同地址。

### 0.3 三种登录源与本项目的对应关系

| provider | 认证流程      | 本项目注册的 scope                | 走哪个用户服务                 |
|----------|-----------|------------------------------|------------------------|
| lab-client | OIDC      | openid, profile, read        | `CustomOidcUserService` |
| github   | 标准 OAuth2 | read:user, user:email        | `CustomOAuth2UserService` |
| google   | OIDC      | openid, profile, email       | `CustomOidcUserService` |

区分标准：scope 是否包含 `openid`——包含则走 OIDC（用户信息在 id_token 里，
本项目由 `CustomOidcUserService` 处理）；不包含则走标准 OAuth2（要额外调
userinfo 端点，`CustomOAuth2UserService` 处理）。

### 0.4 凭据配置注入：两种方式

本项目配置里用的是**环境变量占位符**（`application-desktop.yml` /
`application-laptop.yml`）：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: <你的 GitHub Client ID>
            client-secret: <你的 GitHub Client Secret>
```

### 3. GitHub 返回的用户信息

```json
{
  "login": "username",
  "id": 12345,
  "avatar_url": "https://avatars.githubusercontent.com/...",
  "name": "Full Name",
  "email": "user@example.com"
}
```

`user-name-attribute` 默认是 `login`（Spring Security 默认配置）。

---

## 二、Google OAuth 配置

### 1. 创建 Google OAuth 2.0 Client

1. 登录 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建项目或选择已有项目
3. 进入 **APIs & Services > Credentials**
4. 点击 **Create Credentials > OAuth client ID**
5. 应用类型选择 **Web application**
6. 填写：
    - **Name**: `OAuth2 Lab Client`
    - **Authorized redirect URIs**: `http://localhost:18087/login/oauth2/code/google`
7. 点击 **Create**
8. 记录 Client ID 和 Client Secret

### 2. 配置 OAuth consent screen

1. 进入 **APIs & Services > OAuth consent screen**
2. 选择 **External**（外部用户可用）
3. 添加测试用户（你的 Gmail 地址）
4. 添加 scopes: `openid`, `profile`, `email`

### 3. 配置 application.yml

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: <你的 Google Client ID>
            client-secret: <你的 Google Client Secret>
registration:
  github:
    client-id: ${GITHUB_CLIENT_ID:x}
    client-secret: ${GITHUB_CLIENT_SECRET:x}
  google:
    client-id: ${GOOGLE_CLIENT_ID:x}
    client-secret: ${GOOGLE_CLIENT_SECRET:x}
```

方式一：**设置环境变量**（推荐，凭据不进代码库）

```bash
# Windows（PowerShell）
$env:GITHUB_CLIENT_ID="xxxx"
$env:GITHUB_CLIENT_SECRET="xxxx"
$env:GOOGLE_CLIENT_ID="xxxx"
$env:GOOGLE_CLIENT_SECRET="xxxx"

# 重启后端生效（IDEA 里要在 Run Configuration 的 Environment variables 里配）
```

### 4. Google 的 provider 配置
方式二：直接改 yml 的 `client-id` / `client-secret`（占位符 `x` 表示未配置，
不改的话 GitHub/Google 登录会因凭据无效而失败）。

---

## 一、GitHub

### 1.1 创建 OAuth App

1. 登录 GitHub，右上角头像 → **Settings**
2. 左侧最底部 → **Developer settings**
3. 左侧 → **OAuth Apps**
4. 点 **New OAuth App**（首次进入时按钮显示为 **Register a new application**）

> 说明：GitHub 官方建议新项目优先考虑 GitHub App，但 **OAuth App 依然
> 完全可用、未废弃**，本指南使用 OAuth App（接入最简单）。

### 1.2 填写表单

| 字段                        | 本项目填什么                                                       | 说明                                  |
|---------------------------|---------------------------------------------------------------|-------------------------------------|
| Application name          | `OAuth2 Lab Client`（任意）                                     | 展示给用户的名字                          |
| Homepage URL              | `http://localhost:13000`                                       | 前端地址（`app.frontend-base-url`）       |
| Authorization callback URL | `http://localhost:18090/api/oauth2-client/login/oauth2/code/github` | ★ 必须与 0.2 的回调 URL 完全一致 |

Application description 可留空；Device Flow 不需要勾选。

### 1.3 获取凭据

1. 点 **Register application** 创建
2. 页面显示 **Client ID** —— 复制
3. 点 **Generate a new client secret** → 生成后**立即复制**（只显示一次）

### 1.4 配置到本项目

Spring Security 内置了 Google 的 provider 配置（`CommonOAuth2Provider.GOOGLE`），无需手动配置 provider 段。
如果手动指定，各端点如下：
按 0.4 的方式二选一注入 `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`。

| 端点                  | URL                                             |
|---------------------|-------------------------------------------------|
| authorization-uri   | `https://accounts.google.com/o/oauth2/v2/auth`  |
| token-uri           | `https://oauth2.googleapis.com/token`           |
| user-info-uri       | `https://www.googleapis.com/oauth2/v3/userinfo` |
| user-name-attribute | `sub`                                           |
### 1.5 GitHub 会返回什么用户信息

GitHub 的 userinfo 端点（`https://api.github.com/user`）返回：

```json
{
  "login": "octocat",        // GitHub 用户名 → 本项目标准化为 preferred_username
  "id": 583231,              // 用户唯一标识 → 本项目标准化为 sub（绑定表主键）
  "avatar_url": "https://avatars.githubusercontent.com/u/583231",
  "name": "The Octocat",
  "email": "octocat@github.com"
}
```

> **常见误区**：Spring Security 内置的 GitHub 默认 `user-name-attribute`
> 是 **`id`**（不是 `login`）。`login` 只是 userinfo 返回里的用户名字段。
> 本项目 `CustomOAuth2UserService` 会把 `id → sub`、`login → preferred_username`
> 做标准化，所以这个默认值不用改。

---

## 二、Google

### 2.1 创建 OAuth Client

1. 打开 [Google Cloud Console](https://console.cloud.google.com/)，创建项目或选已有项目
2. **APIs & Services → Credentials**（凭据）
3. 点 **Create credentials**（创建凭据）→ **OAuth client ID**
4. Application type 选 **Web application**
5. 填写：
    - **Name**: `OAuth2 Lab Client`（任意）
    - **Authorized redirect URIs**（授权重定向 URI）: 点 **Add URI**，填
      `http://localhost:18090/api/oauth2-client/login/oauth2/code/google`
      （★ 必须与 0.2 的回调 URL 完全一致）
6. 点 **Create** → 弹窗中记录 **Client ID** 和 **Client Secret**

> 服务端流程（本项目）只需要 Authorized redirect URIs，
> Authorized JavaScript origins 不用填。

### 2.2 配置 OAuth consent screen（必做，否则登录报 access_denied）

1. **APIs & Services → OAuth consent screen**（OAuth 同意屏幕）
2. User type 选 **External**（外部用户可用）
3. 依次填写 App 名称、支持邮箱等必填项
4. **添加测试用户**：加你的 Gmail 地址（未发布前只有测试用户能登录）
5. 确认 scopes 包含 `openid`、`profile`、`email`（本项目注册的 scope）
6. 上线前把 **Publishing status** 改为 **In production**（否则只有测试用户可用）

### 2.3 配置到本项目

按 0.4 的方式二选一注入 `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`。

### 2.4 Google 的内置默认端点（框架自动使用，无需配置）

Spring Boot 内置了 Google 的 provider 默认值（`CommonOAuth2Provider.GOOGLE`），
`registration.google` 只需要填凭据。手动对照时可参考：

| 项                  | 值                                                     |
|--------------------|------------------------------------------------------|
| authorization-uri  | `https://accounts.google.com/o/oauth2/v2/auth`        |
| token-uri          | `https://oauth2.googleapis.com/token`                 |
| user-info-uri      | `https://www.googleapis.com/oauth2/v3/userinfo`       |
| jwk-set-uri        | `https://www.googleapis.com/oauth2/v3/certs`          |
| user-name-attribute | `sub`                                                |

### 2.5 Google 走的是 OIDC 流程

- 本项目 `google` 注册的 scope 含 `openid` → 走 `CustomOidcUserService`
- OIDC 下用户信息在 **id_token**（JWT）里直接携带（`sub`、`name`、
  `email`、`picture`），不需要额外调 userinfo 端点
- `sub` 是 Google 用户唯一标识（本项目绑定表用它）

---

## 三、验证三方认证
## 三、验证清单

1. 启动 client 模块：`http://localhost:18087`
2. 在首页选择 GitHub 或 Google 登录
3. 在对应平台完成授权
4. 回调后查看用户信息是否正确显示
1. 三个后端 + 前端启动（步骤见 [OAuth2认证流程与多环境部署指南](../OAuth2认证流程与多环境部署指南.md)）
2. 前端登录页（`http://localhost:13000/#/login`）点 **GitHub 登录**
   → 应跳转到 GitHub 授权页 → 授权后回跳
3. 未绑定过 → 应跳到绑定页 → 绑定本地账号后完成登录
4. 重新登录 → 应直接登录成功（已绑定）
5. 用 Google 重复 2~4
6. 排查时看 client-server 日志（`logging.level.org.springframework.security.oauth2.client: DEBUG`
   已默认开启），关键节点都有日志：发起授权、回调、换 Token、userinfo

---

## 四、常见问题

### redirect_uri 不匹配

- GitHub/Google 严格控制 redirect_uri，必须完全一致
- 注意：`localhost` vs `127.0.0.1` 被视为不同的地址
- 推荐的 redirect_uri: `http://localhost:18087/login/oauth2/code/{provider}`
- 平台严格控制回调 URL，必须与 `redirect-uri` 完全一致
- 三处要统一：平台登记的 URL、`registration.*.redirect-uri`、实际访问地址
- 注意 `localhost` vs `127.0.0.1` 是不同的地址

### 回调后 401 / 登录失败

### 回调后 401
- 检查凭据是否真的生效（环境变量没配上时是占位符 `x`，见 0.4）
- 检查 `client-id` / `client-secret` 与平台是否一致
- 看日志 `org.springframework.security.oauth2.client: DEBUG`

- 检查 `client-id` 和 `client-secret` 是否正确
- 查看客户端日志 `org.springframework.security.oauth2.client: DEBUG`
### 报 authorization_request_not_found

- 常见原因：从"发起授权"到"回调"之间 Session 丢失（如跨域、重启、
  Cookie 问题）
- 本项目 failureHandler 对这类错误会自动重试一次；仍失败则检查
  `oauth2.base-url` 与浏览器实际访问地址是否同源（Cookie 是否带得上）

### Google 报 access_denied

- 检查 OAuth consent screen 是否已发布
- 确认测试用户已添加到允许列表
- 检查 OAuth consent screen：User type 是否 External、测试用户是否添加、
  App 是否已发布（In production）
- 确认 scope 包含 `openid profile email`

### 为什么 GitHub 登录后拿到的用户名是数字/拿不到 login

- Spring Security 用 `user-name-attribute` 构造认证对象，GitHub 默认是
  `id`（数字）。本项目的 `CustomOAuth2UserService` 会做字段标准化
  （`login → preferred_username`），登录后业务侧用标准化的
  `preferred_username` 即可，不需要改 `user-name-attribute`

---

## 五、关联文档

- [OAuth2认证原理](OAuth2认证原理.md) —— 授权码流程与角色（机制层）
- [OAuth2认证流程与多环境部署指南](../OAuth2认证流程与多环境部署指南.md) —— 部署、端口、网关
- [项目启动顺序](../architecture/项目启动顺序.md) —— 启动步骤

