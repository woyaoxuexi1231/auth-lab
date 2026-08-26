package com.lab.securitysession.service;

import com.lab.securitysession.entity.User;
import com.lab.securitysession.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserDetailsService — 从 DB 加载用户供 Spring Security 认证。
 *
 * <p>被 DaoAuthenticationProvider 在登录时调用，以及 SecurityContextHolderFilter
 * 从 Session 恢复认证时调用。</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    public UserDetailsServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ① 按用户名查库：登录时由 DaoAuthenticationProvider 调用此方法取用户
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));  // 查不到必须抛此异常，否则认证流程会 500

        // ② 查角色表，拼装权限集合（角色名 → SimpleGrantedAuthority）
        List<String> roles = userMapper.findRolesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)  // 每个角色名包装成授权对象，供 hasRole()/hasAuthority() 匹配
                .collect(Collectors.toList());

        // ③ 包装成 Spring Security 标准 UserDetails 返回，后续密码比对在此对象上进行
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())  // 数据库中的 BCrypt 密文，由 PasswordEncoder.matches() 与输入明文比对
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.getEnabled())) // null → disabled（enabled 字段为 null 视为禁用）
                .build();
    }
}
