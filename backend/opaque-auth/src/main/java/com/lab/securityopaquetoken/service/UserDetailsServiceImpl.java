package com.lab.securityopaquetoken.service;

import com.lab.securityopaquetoken.entity.User;
import com.lab.securityopaquetoken.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserDetailsService — 从 DB 加载用户供认证使用。
 *
 * <p>仅登录时被调用（AuthenticationManager → DaoAuthenticationProvider），
 * 后续请求认证走 Redis（TokenAuthenticationFilter），不再查数据库。</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    public UserDetailsServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 登录时按用户名查库，查不到抛 UsernameNotFoundException（认证流程会按"凭证错误"处理）
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 查角色并包装为 Spring Security 授权对象
        List<String> roles = userMapper.findRolesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // 返回标准 UserDetails：认证器用其中的 password 与登录输入比对（BCrypt.matches）
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())  // BCrypt 密文，仅用于比对，绝不回传前端
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))  // 禁用账号直接禁止登录
                .build();
    }
}
