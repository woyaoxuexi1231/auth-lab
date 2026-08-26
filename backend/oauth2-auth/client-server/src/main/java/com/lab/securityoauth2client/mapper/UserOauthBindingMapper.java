package com.lab.securityoauth2client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.securityoauth2client.entity.UserOauthBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * OAuth 绑定 Mapper — 查询绑定关系。
 */
@Mapper
public interface UserOauthBindingMapper extends BaseMapper<UserOauthBinding> {
    @Select("SELECT * FROM user_oauth_binding WHERE provider = #{provider} AND provider_user_id = #{providerUserId} LIMIT 1")
    UserOauthBinding findByProviderAndProviderUserId(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId);  // OAuth 登录时查绑定（三方用户 → 本地用户）

    @Select("SELECT * FROM user_oauth_binding WHERE local_user_id = #{localUserId}")
    List<UserOauthBinding> findByLocalUserId(@Param("localUserId") Long localUserId);  // 某本地用户的绑定列表

    @Select("SELECT COUNT(*) FROM user_oauth_binding WHERE local_user_id = #{localUserId}")
    int countByLocalUserId(@Param("localUserId") Long localUserId);  // 绑定数量（解绑安全检查用）
}
