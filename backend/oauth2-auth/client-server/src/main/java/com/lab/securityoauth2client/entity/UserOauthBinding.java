package com.lab.securityoauth2client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth 绑定实体 — 映射 user_oauth_binding 表。
 *
 * <p>每一条记录表示一个本地用户与一个三方 OAuth 账号的绑定关系。
 * 一个本地用户可以绑定多个三方账号（如同时绑定 GitHub 和 Google）。</p>
 */
@Data
@TableName("user_oauth_binding")
public class UserOauthBinding {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键，自增
    @TableField("local_user_id")
    private Long localUserId;           // 关联本地用户
    private String provider;            // 三方提供方（github/google/lab-client）
    @TableField("provider_user_id")
    private String providerUserId;      // Provider 侧用户唯一标识（sub）
    @TableField("provider_username")
    private String providerUsername;    // Provider 侧用户显示名
    private String email;               // Provider 侧邮箱（展示用）
    @TableField("avatar_url")
    private String avatarUrl;           // Provider 侧头像
    @TableField("bind_time")
    private LocalDateTime bindTime;     // 绑定时间
}
