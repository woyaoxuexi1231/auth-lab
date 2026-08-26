package com.lab.securityjwtauthserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 — 映射 security_lab_jwt.user 表。
 *
 * <p>MyBatis Plus 注解说明：
 * <ul>
 *   <li>{@code @TableId(type = IdType.AUTO)} — 主键自增</li>
 *   <li>{@code @TableField("create_time")} — 字段名映射（下划线 → 驼峰）</li>
 *   <li>{@code @TableName("user")} — 显式指定表名</li>
 * </ul>
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;                // 主键，自增

    private String username;        // 登录账号（登录与 JWT 恢复认证时按此查库）

    private String password;        // BCrypt 加密后的密文，非明文

    private String email;           // 邮箱（业务字段）

    private Boolean enabled;        // 是否启用（禁用后无法登录）

    @TableField("create_time")
    private LocalDateTime createTime;  // 创建时间
}
