package com.lab.securityoauth2authserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色实体 — 映射 security_lab_oauth2.role 表。
 *
 * <p>角色名遵循 Spring Security 约定：以 "ROLE_" 开头。
 * hasRole("ADMIN") 自动匹配 "ROLE_ADMIN"。</p>
 */
@Data
@TableName("role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;            // ROLE_USER, ROLE_ADMIN
    private String description;     // 角色描述
}
