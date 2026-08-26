package com.lab.securityoauth2authserver.service;

import com.lab.securityoauth2authserver.entity.User;
import com.lab.securityoauth2authserver.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserDetailsService — 从 DB 加载用户（供 OAuth2 登录流程中的 DaoAuthenticationProvider 使用）。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    public UserDetailsServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 登录时按用户名查库（OAuth2 授权流程的 formLogin 同样走这里）
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 查角色 → 转为 GrantedAuthority（hasRole("ADMIN") 匹配 ROLE_ADMIN）
        List<String> roles = userMapper.findRolesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // 包装标准 UserDetails：密码用于 BCrypt 比对；enabled=false 禁止登录
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                .build();
    }
}
