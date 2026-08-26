package com.lab.securityoauth2authserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.securityoauth2authserver.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 用户 Mapper — BaseMapper + 自定义查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE username = #{username} LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);

    @Select("SELECT r.name FROM role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);
}
