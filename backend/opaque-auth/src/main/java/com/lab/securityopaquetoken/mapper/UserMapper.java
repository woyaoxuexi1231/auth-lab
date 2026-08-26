package com.lab.securityopaquetoken.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.securityopaquetoken.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 用户 Mapper — 继承 BaseMapper + 自定义查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE username = #{username} LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);  // 登录查库（LIMIT 1 防止重名返回多行）

    @Select("SELECT r.name FROM role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);  // 联表查角色名（登录时拼权限用）
}
