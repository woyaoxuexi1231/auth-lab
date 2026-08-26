package com.lab.securityoauth2client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地用户实体 — 映射 security_lab_oauth2_client.local_user 表。
 *
 * <p>与其它模块不同，这里使用 local_user 表（而非 user），
 * 是因为 OAuth2 客户端模块需要同时管理本地用户和 OAuth 绑定关系。</p>
 */
@Data
@TableName("local_user")
public class LocalUser {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键，自增
    private String username;            // 本地登录账号（表单登录时按此查库）
    @TableField("password_hash")
    private String passwordHash;        // BCrypt 密文 — 可为空（纯 OAuth 登录用户无密码）
    private String email;               // 邮箱（注册时可空）
    @TableField("avatar_url")
    private String avatarUrl;           // 头像（预留）
    private Boolean enabled;            // 是否启用
    @TableField("create_time")
    private LocalDateTime createTime;   // 创建时间
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;  // 最近登录时间
}
