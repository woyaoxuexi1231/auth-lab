package com.lab.securityopaquetoken.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 — 映射 security_lab_opaque.user 表。
 */
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键，自增
    private String username;        // 登录账号（登录接口按此查库）
    private String password;        // BCrypt 密文
    private String email;           // 业务字段，与认证无关
    private Boolean enabled;        // 是否启用
    @TableField("create_time")
    private LocalDateTime createTime;  // 创建时间
}
