package com.lab.securityoauth2client.service;

import com.lab.securityoauth2client.entity.LocalUser;
import com.lab.securityoauth2client.mapper.LocalUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从 local_user 表加载用户 — 供 formLogin 使用。
 * 所有本地用户统一分配 ROLE_USER。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final LocalUserMapper localUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LocalUser localUser = localUserMapper.findByUsername(username);
        if (localUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return User.builder()
                .username(localUser.getUsername())
                .password(localUser.getPasswordHash())
                .authorities("ROLE_USER")                                   // 所有本地用户默认权限
                .disabled(!Boolean.TRUE.equals(localUser.getEnabled()))
                .build();
    }
}
