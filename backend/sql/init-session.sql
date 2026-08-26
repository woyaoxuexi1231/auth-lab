-- ============================================================
-- security-session 模块 - 独立数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS security_lab_session
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE security_lab_session;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(200) NOT NULL COMMENT '密码（BCrypt）',
    `email`       VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
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

INSERT INTO `role` (`name`)
VALUES ('ROLE_USER'),
       ('ROLE_ADMIN');

-- ============================================================
-- 初始化测试用户：admin / user，密码统一 123456（BCrypt）
-- 与 README「测试账号」保持一致
-- ============================================================
INSERT IGNORE INTO `user` (`username`, `password`, `email`, `enabled`)
VALUES ('admin', '$2a$10$ZHPv3g4AHFjCgf/peBAta.p6HxkrA96Mvns8Rz/1p7gIxXjgcnDGO', 'admin@lab.com', 1),
       ('user',  '$2a$10$ZHPv3g4AHFjCgf/peBAta.p6HxkrA96Mvns8Rz/1p7gIxXjgcnDGO', 'user@lab.com', 1);

-- admin -> ROLE_USER + ROLE_ADMIN；user -> ROLE_USER
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
         CROSS JOIN `role` r
WHERE (u.username = 'admin' AND r.name IN ('ROLE_USER', 'ROLE_ADMIN'))
   OR (u.username = 'user' AND r.name = 'ROLE_USER');
