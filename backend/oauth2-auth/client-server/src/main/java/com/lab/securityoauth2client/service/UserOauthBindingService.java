package com.lab.securityoauth2client.service;

import com.lab.securityoauth2client.entity.UserOauthBinding;
import com.lab.securityoauth2client.mapper.UserOauthBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth 绑定服务 — 绑定/解绑/查询 OAuth 三方账号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserOauthBindingService {

    private final UserOauthBindingMapper bindingMapper;

    /** 绑定 OAuth 账号到本地用户 */
    public UserOauthBinding bind(Long localUserId, String provider, String providerUserId,
                                 String providerUsername, String email, String avatarUrl) {
        UserOauthBinding binding = new UserOauthBinding();
        binding.setLocalUserId(localUserId);
        binding.setProvider(provider);
        binding.setProviderUserId(providerUserId);  // 三方账号唯一 ID（sub）
        binding.setProviderUsername(providerUsername);
        binding.setEmail(email);
        binding.setAvatarUrl(avatarUrl);
        binding.setBindTime(LocalDateTime.now());
        bindingMapper.insert(binding);  // 唯一性由 (provider, provider_user_id) 联合约束兜底
        log.info("OAuth 绑定成功: localUser={}, provider={}", localUserId, provider);
        return binding;
    }

    public List<UserOauthBinding> listByLocalUserId(Long localUserId) {
        return bindingMapper.findByLocalUserId(localUserId);  // 某本地用户的所有三方绑定
    }

    /** 解绑 — 删除绑定记录 */
    public void unbind(Long bindingId) {
        bindingMapper.deleteById(bindingId);
        log.info("OAuth 绑定已解除: bindingId={}", bindingId);
    }

    public int countByLocalUserId(Long localUserId) {
        return bindingMapper.countByLocalUserId(localUserId);  // 绑定数量（解绑安全检查用）
    }

    public UserOauthBinding findById(Long id) { return bindingMapper.selectById(id); }
}
