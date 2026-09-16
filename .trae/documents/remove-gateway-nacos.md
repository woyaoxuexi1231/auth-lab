# 删除 Spring Cloud Gateway 与 Nacos，降低系统复杂性

## Context（背景）

当前架构中 `backend/gateway` 模块（端口 18090）作为统一入口，通过 Nacos 服务发现（`lb://`）把 `/api/**` 路由到 6 个认证服务；所有服务都依赖 `spring-cloud-starter-alibaba-nacos-discovery`，父 pom 还引入了 Spring Cloud / Alibaba BOM 与 `nacos.*` 打包属性。这套微服务基础设施对 6 个直连即可运行的认证服务来说过重，用户决定整体删除。

**目标**：删除 Gateway 模块 + Nacos 服务发现，前端改用 Vite 代理按路径把 `/api/**` 分发到各服务直连端口，OAuth2 的 base-url 改为前端源地址 `http://localhost:13008`（vite dev 端口），所有浏览器请求保持同源，行为与现在一致。

各服务直连端口：session 18082 / jwt 18083 / opaque 18088 / oauth2-auth-server 18085 / oauth2-resource-server 18086 / oauth2-client-server 18087。

## 一、删除项

1. **删除整个 `backend/gateway/` 目录**：`pom.xml`、`src/main/resources/application.yml`、`src/main/java/com/lab/gateway/GatewayApplication.java`、`build.sh`。
2. **父 pom `backend/pom.xml`**：
   - `<modules>` 移除 `<module>gateway</module>`；
   - `<properties>` 移除 `spring-cloud.version`、`spring-cloud-alibaba.version`；
   - `dependencyManagement` 移除 `spring-cloud-dependencies` 与 `spring-cloud-alibaba-dependencies` 两个 import；
   - dev/prod profile 移除 `nacos.host` / `nacos.port` / `nacos.ip`；
   - `<description>` 去掉 "Gateway" 字样。

## 二、Nacos 依赖与配置清理

3. **6 个服务 pom 移除 `spring-cloud-starter-alibaba-nacos-discovery` 依赖块**：
   `backend/{session-auth,jwt-auth,opaque-auth}/pom.xml`、`backend/oauth2-auth/{auth-server,resource-server,client-server}/pom.xml`（各 1 处）。
4. **10 个 yml 删除 `spring.cloud.nacos` 块（dev+prod）**：
   session-auth、jwt-auth、opaque-auth 的 `application-dev.yml` / `application-prod.yml`；
   oauth2-auth 的 auth-server / resource-server / client-server 的 `application-dev.yml` / `application-prod.yml`。
   删除后保留 datasource / redis / security 等原有配置。

## 三、OAuth2 base-url 改为前端源地址

5. **父 pom `backend/pom.xml` profiles**：
   - dev：`oauth2.base-url` `http://localhost:18090` → `http://localhost:13008`；
   - prod：`http://CHANGE_ME:18090` → `http://CHANGE_ME`（前端公网源，不带端口）。
   该属性经 `@oauth2.base-url@` 注入到 auth-server 的 issuer / redirect-uri、client-server 的 provider 四 URI / redirect-uri、resource-server 的 issuer-uri，无需逐行改 yml。
6. **auth-server `application.yml`**：`app.oauth2.allowed-logout-origins` `http://localhost:18090` → `http://localhost:13008`（`SessionLogoutController` 精确白名单比对，漏改会 400），同步更新注释。
7. **`backend/sql/init-oauth2.sql`**：第 109 行 `redirect_uris` → `http://localhost:13008/api/oauth2-client/login/oauth2/code/lab-client`，更新第 99 行注释。
   ⚠ 若数据库已存在该行，`INSERT IGNORE` 不会覆盖，需手动 `UPDATE oauth2_registered_client SET redirect_uris='http://localhost:13008/api/oauth2-client/login/oauth2/code/lab-client' WHERE client_id='lab-client'`。

## 四、前端 Vite 代理

8. **`frontend/vite.config.js`**：单条 `'/api' → http://localhost:18090` 替换为 6 条路径前缀代理（均 `changeOrigin: true`，路径原样透传，无需 rewrite）：
   - `/api/session` → `http://localhost:18082`
   - `/api/jwt` → `http://localhost:18083`
   - `/api/opaque` → `http://localhost:18088`
   - `/api/oauth2-client` → `http://localhost:18087`
   - `/api/oauth2-auth` → `http://localhost:18085`
   - `/api/oauth2-resource` → `http://localhost:18086`
   - **额外加一条** `/.well-known` → `http://localhost:18085`：resource-server 的 `NimbusJwtDecoder` 会 fetch `{issuer}/.well-known/openid-configuration`（auth-server 未改写 metadata 端点，仍是 SAS 默认根路径）来发现 `jwks_uri`，issuer=13008 时必须经 vite 代理才能命中 auth-server。
9. **`frontend/.env.development`**：`VITE_AUTH_SERVER_ORIGIN` `http://localhost:18090` → `http://localhost:13008`（OAuth2Auth.vue postMessage 白名单；新架构下弹窗全同源，该值作为兜底）。

## 五、配置一致性修正

10. **client-server `application-dev.yml` / `application-prod.yml`**：`app.frontend-base-url` `http://localhost:13000` → `http://localhost:13008`（`SecurityConfig` 用它把登录成功重定向回前端 `/#/bind`、`/#/oauth2`；现配置 13000 与真实 vite 端口 13008 不符，本就跳错端口）。

## 六、文档与脚本

11. **`backend/docker.sh`**：第 5 行注释去掉 Nacos；末尾"通过 Gateway :18090 统一访问"改为各服务直连端口 / 前端 `http://localhost:13008`。
12. **`README.md`**：简介、目录结构、前置依赖（去 Nacos）、后端启动命令（`-pl` 列表去掉 gateway）、"Gateway 统一入口 18090" 相关描述更新。
13. **`backend/docs/OAuth2认证流程与多环境部署指南.md`**：更新架构图/端口表/流程步骤/配置表/生产部署章节中约 40 处 18090、gateway、nacos、13000 引用 → 13008 / 直连端口；nginx 生产方案补充 `/.well-known` 与 `/api/oauth2-auth` 等 location 指向各服务（原网关职责）。
14. **`backend/docs/auth-principles/OAUTH2-SETUP.md`**：第 42 / 49-51 / 178 / 232 行回调 URL 18090 → 13008；177 行 Homepage URL 13000 → 13008。
15. `docs/auth-principles/OAuth2认证原理.md` 无 18090/nacos 引用，不动；`AuthorizationServerConfig.java` 等注释里的 18090/网关描述可保留（纯注释）。

## 验证步骤

1. 根目录 `mvn clean package -DskipTests`，确认无 gateway / spring-cloud / nacos 依赖报错。
2. 起 MySQL/Redis；执行或更新 `init-oauth2.sql`（redirect_uris=13008）；若旧库已初始化，执行上面的 UPDATE。
3. 分模块启动 6 个服务（`-pl` 去掉 gateway）；日志中不应出现任何 nacos 连接尝试。
4. `cd frontend && npm run dev` → 打开 `http://localhost:13008/#/auth-lab/session`。
5. 直连探活（走 vite 代理）：session / jwt / opaque 三套 登录→`/me` 全链路。
6. resource-server 验签：`curl http://localhost:13008/.well-known/openid-configuration` 返回 JSON 且 `jwks_uri` 指向 13008；再走一次 OAuth2 登录流验证 `/api/oauth2-resource/items` 返回 200。
7. OAuth2 完整流程：OAuth2Auth 页点 Lab 登录 → 弹窗在 13008 上登录/consent → 回调 13008 → postMessage 回主窗 → 显示已登录。
8. 登出弹窗：确认 auth-server 不返回 400（allowed-logout-origins=13008 生效）。
9. 建议用无痕窗口验证，避免旧 Cookie 与 302 缓存干扰。
