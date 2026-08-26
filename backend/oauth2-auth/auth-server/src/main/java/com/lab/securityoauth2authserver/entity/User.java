package com.lab.securityoauth2authserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 — 映射 security_lab_oauth2.user 表。
 * 比其它模块多 avatar_url 字段（供 OIDC userinfo 端点使用）。
 */
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键，自增
    private String username;        // 登录账号（OAuth2 授权流程登录时按此查库）
    private String password;        // BCrypt 密文
    private String email;           // 邮箱（OIDC userinfo 端点返回）
    @TableField("avatar_url")
    private String avatarUrl;       // 头像 URL（OIDC 返回）
    private Boolean enabled;        // 是否启用
    @TableField("create_time")
    private LocalDateTime createTime;  // 创建时间
}
