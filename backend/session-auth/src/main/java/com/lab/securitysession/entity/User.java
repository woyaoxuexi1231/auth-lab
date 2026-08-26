package com.lab.securitysession.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 — 映射 security_lab_session.user 表。
 *
 * <p>字段：id、username、password（BCrypt 密文）、email、enabled、createTime。</p>
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;                // 主键，自增

    private String username;        // 登录账号（唯一，认证时按此字段查库）

    private String password;        // BCrypt 加密密文（绝不能存明文）

    private String email;           // 邮箱（业务字段，与认证无关）

    private Boolean enabled;        // 是否启用（false 时即使密码正确也无法登录）

    @TableField("create_time")
    private LocalDateTime createTime;  // 创建时间
}
