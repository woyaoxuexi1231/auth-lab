-- ============================================================
-- security-oauth2-client 模块 - 独立数据库
-- v2: 本地账户 + OAuth2 三方绑定
-- ============================================================
CREATE DATABASE IF NOT EXISTS security_lab_oauth2_client
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE security_lab_oauth2_client;

-- ============================================================
-- 本地用户表（表单登录 / 密码认证）
-- ============================================================
CREATE TABLE IF NOT EXISTS `local_user`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(50)  NOT NULL COMMENT '用户名（登录用）',
    `password_hash`   VARCHAR(200) NOT NULL COMMENT 'BCrypt 密码哈希',
    `email`           VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `avatar_url`      VARCHAR(500)          DEFAULT NULL COMMENT '头像 URL',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `last_login_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后登录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='本地用户表';

-- ============================================================
-- OAuth 三方账号绑定表
-- 只有完成绑定后才插入记录（local_user_id NOT NULL）
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_oauth_binding`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `local_user_id`     BIGINT       NOT NULL COMMENT '关联的本地用户ID',
    `provider`          VARCHAR(50)  NOT NULL COMMENT 'OAuth2 提供方（github/google/lab-client）',
    `provider_user_id`  VARCHAR(200) NOT NULL COMMENT 'Provider 的用户唯一标识（sub）',
    `provider_username` VARCHAR(100)          DEFAULT NULL COMMENT '三方账号的显示名',
    `email`             VARCHAR(100)          DEFAULT NULL COMMENT '三方账号的邮箱',
    `avatar_url`        VARCHAR(500)          DEFAULT NULL COMMENT '三方账号的头像 URL',
    `bind_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_user` (`provider`, `provider_user_id`),
    INDEX `idx_local_user_id` (`local_user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='OAuth 三方账号绑定表';

-- ============================================================
-- 初始化本地测试用户：admin / user，密码统一 123456（BCrypt）
-- 与 README「测试账号」保持一致
-- ============================================================
INSERT IGNORE INTO `local_user` (`username`, `password_hash`, `email`, `enabled`)
VALUES ('admin', '$2a$10$ZHPv3g4AHFjCgf/peBAta.p6HxkrA96Mvns8Rz/1p7gIxXjgcnDGO', 'admin@lab.com', 1),
       ('user',  '$2a$10$ZHPv3g4AHFjCgf/peBAta.p6HxkrA96Mvns8Rz/1p7gIxXjgcnDGO', 'user@lab.com', 1);
