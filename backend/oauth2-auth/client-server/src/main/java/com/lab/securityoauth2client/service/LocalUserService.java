package com.lab.securityoauth2client.service;

import com.lab.securityoauth2client.entity.LocalUser;
import com.lab.securityoauth2client.mapper.LocalUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 本地用户服务 — 注册、查询、密码检查。
 *
 * <p>注册时自动 BCrypt 加密密码、检查用户名和邮箱唯一性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalUserService {

    private final LocalUserMapper localUserMapper;
    private final PasswordEncoder passwordEncoder;

    /** 注册本地用户 — 密码 BCrypt 加密后存储 */
    public LocalUser register(String username, String rawPassword, String email) {
        // 用户名唯一性校验（并发下可能竞态，学习项目接受；生产可用唯一索引兜底）
        if (localUserMapper.countByUsername(username) > 0) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        // 邮箱唯一性校验（邮箱可空，非空时才校验）
        if (email != null && !email.isBlank() && localUserMapper.countByEmail(email) > 0) {
            throw new IllegalArgumentException("邮箱已被注册: " + email);
        }
        LocalUser user = new LocalUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));  // ★ 明文 → BCrypt（绝不留存明文）
        user.setEmail(email);
        user.setEnabled(true);
        user.setCreateTime(LocalDateTime.now());
        user.setLastLoginTime(LocalDateTime.now());
        localUserMapper.insert(user);
        log.info("本地用户注册成功: {}", username);
        return user;
    }

    public LocalUser findByUsername(String username) { return localUserMapper.findByUsername(username); }
    public LocalUser findById(Long id) { return localUserMapper.selectById(id); }

    /** 检查用户是否设置了密码（用于安全检查：至少保留一种登录方式） */
    public boolean hasPassword(Long userId) {
        LocalUser user = findById(userId);
        // 密码哈希为空视为"无密码"（纯三方登录用户）
        return user != null && user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
    }
}
