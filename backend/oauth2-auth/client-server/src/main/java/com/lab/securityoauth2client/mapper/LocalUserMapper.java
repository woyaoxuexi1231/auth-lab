package com.lab.securityoauth2client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.securityoauth2client.entity.LocalUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 本地用户 Mapper — 继承 BaseMapper + 自定义查询（用户名查用户、计数）。
 */
@Mapper
public interface LocalUserMapper extends BaseMapper<LocalUser> {
    @Select("SELECT * FROM local_user WHERE username = #{username} LIMIT 1")
    LocalUser findByUsername(@Param("username") String username);  // 登录查库（LIMIT 1 防重名多行）

    @Select("SELECT COUNT(*) FROM local_user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);  // 注册时唯一性校验

    @Select("SELECT COUNT(*) FROM local_user WHERE email = #{email}")
    int countByEmail(@Param("email") String email);  // 注册时邮箱唯一性校验
}
