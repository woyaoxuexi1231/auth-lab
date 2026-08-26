-- ============================================================
-- security-oauth2-auth-server 模块 - 独立数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS security_lab_oauth2
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE security_lab_oauth2;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(200) NOT NULL COMMENT '密码（BCrypt）',
    `email`       VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `avatar_url`  VARCHAR(500)          DEFAULT NULL COMMENT '头像URL',
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role`
(
    `id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_role`
(
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 用 INSERT IGNORE 而不是 INSERT：本文件要能“安全重跑”（旧库补数据）。
-- 若这里用普通 INSERT，角色已存在时会因唯一键报错中断脚本，导致后面的 user_role 补不上（管理页 403）。
INSERT IGNORE INTO `role` (`name`)
VALUES ('ROLE_USER'),
       ('ROLE_ADMIN');

-- ============================================================
-- 初始化测试用户：admin / user，密码统一 123456（BCrypt）
-- 注意：密码必须带 {bcrypt} 前缀。auth-server 用 DelegatingPasswordEncoder，无前缀会报
-- “There is no PasswordEncoder mapped for the id null”，登录必然失败。
-- 与 README「测试账号」保持一致
-- ============================================================
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `avatar_url`, `enabled`)
VALUES ('admin', '{bcrypt}$2a$10$ZHPv3g4AHFjCgf/peBAta.p6HxkrA96Mvns8Rz/1p7gIxXjgcnDGO', 'admin@lab.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin', 1),
       ('user',  '{bcrypt}$2a$10$ZHPv3g4AHFjCgf/peBAta.p6HxkrA96Mvns8Rz/1p7gIxXjgcnDGO', 'user@lab.com',  'https://api.dicebear.com/7.x/avataaars/svg?seed=user', 1);

-- admin -> ROLE_USER + ROLE_ADMIN；user -> ROLE_USER
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
         CROSS JOIN `role` r
WHERE (u.username = 'admin' AND r.name IN ('ROLE_USER', 'ROLE_ADMIN'))
   OR (u.username = 'user' AND r.name = 'ROLE_USER');

-- ============================================================
-- OAuth2 客户端注册表 — 客户端不再硬编码在代码里，从此表读取
-- 授权服务器自带管理页面：/api/oauth2-auth/clients（需 ROLE_ADMIN 登录）
-- ============================================================
CREATE TABLE IF NOT EXISTS `oauth2_registered_client`
(
    `id`                            VARCHAR(100) NOT NULL COMMENT '主键（UUID）',
    `client_id`                     VARCHAR(100) NOT NULL COMMENT '客户端ID',
    `client_id_issued_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    `client_secret`                 VARCHAR(200)          DEFAULT NULL COMMENT '客户端密钥（{bcrypt} 密文）',
    `client_name`                   VARCHAR(200) NOT NULL COMMENT '客户端名称',
    `client_authentication_methods` VARCHAR(255) NOT NULL COMMENT '认证方式（逗号分隔）',
    `authorization_grant_types`     VARCHAR(255) NOT NULL COMMENT '授权类型（逗号分隔）',
    `redirect_uris`                 TEXT                  DEFAULT NULL COMMENT '回调地址（逗号分隔）',
    `post_logout_redirect_uris`     TEXT                  DEFAULT NULL COMMENT '登出回调地址（逗号分隔）',
    `scopes`                        VARCHAR(255) NOT NULL COMMENT '授权范围（逗号分隔）',
    `require_proof_key`             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '要求 PKCE',
    `require_authorization_consent` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '需要授权确认',
    `access_token_ttl_seconds`      INT          NOT NULL DEFAULT 3600 COMMENT '访问令牌有效期（秒）',
    `refresh_token_ttl_seconds`     INT          NOT NULL DEFAULT 604800 COMMENT '刷新令牌有效期（秒）',
    `reuse_refresh_tokens`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '刷新令牌是否复用',
    `create_time`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- 初始化内置客户端 lab-client（密钥明文 secret，BCrypt 密文）
-- 注意：redirect_uris 是环境相关的（本地默认 localhost:18090）。
-- 换环境（Docker / 服务器 / 生产）时，请在客户端管理页删除重建，或用 SQL 更新本行。
-- ============================================================
INSERT IGNORE INTO `oauth2_registered_client`
    (`id`, `client_id`, `client_secret`, `client_name`, `client_authentication_methods`,
     `authorization_grant_types`, `redirect_uris`, `scopes`,
     `require_authorization_consent`, `access_token_ttl_seconds`, `refresh_token_ttl_seconds`, `reuse_refresh_tokens`)
VALUES ('lab-client-001', 'lab-client', '{bcrypt}$2a$10$wMGCrH.dDAfQfs9iJjF0Ae..cRQs1/EZtp37CNabJnZHsvrxEG58K',
        '实验室OAuth2客户端', 'client_secret_basic,client_secret_post',
        'authorization_code,refresh_token',
        'http://localhost:18090/api/oauth2-client/login/oauth2/code/lab-client',
        'openid,profile,read',
        1, 3600, 604800, 0);
