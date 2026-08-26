package com.lab.securityjwtauthserver.service;

import com.lab.securityjwtauthserver.entity.User;
import com.lab.securityjwtauthserver.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserDetailsService 实现 — 从数据库加载用户信息供 Spring Security 认证。
 *
 * <p>每当用户登录或 JWT 过滤器恢复认证时，此方法被调用。
 * 流程：用户名 → 查 DB User → 查 Role → 构建 Spring Security UserDetails。</p>
 *
 * <p>{@code !Boolean.TRUE.equals(user.getEnabled())} 写法说明：
 * 用 Boolean.TRUE.equals() 防止 enabled 为 null 时 NPE，
 * null 被视为 disabled（账号不可用）。</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    public UserDetailsServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ① 从 DB 查用户
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // ② 查用户角色 → 转为 Spring Security 权限
        List<String> roles = userMapper.findRolesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)       // 角色名 → 权限对象
                .collect(Collectors.toList());

        // ③ 构建 Spring Security UserDetails（User 是框架内置类）
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())           // BCrypt 密文
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.getEnabled())) // null → disabled
                .build();
    }
}
