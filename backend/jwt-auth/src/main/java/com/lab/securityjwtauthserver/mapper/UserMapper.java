package com.lab.securityjwtauthserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.securityjwtauthserver.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 用户 Mapper — MyBatis Plus 数据访问层。
 *
 * <p>继承 {@code BaseMapper<User>} 获得内置 CRUD 方法（insert/selectById/updateById/deleteById）。
 * 自定义查询使用 {@code @Select} 注解 SQL。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 根据用户名查用户（用于登录认证） */
    @Select("SELECT * FROM user WHERE username = #{username} LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);

    /** 查询用户拥有的角色名列表（用于构建 GrantedAuthority） */
    @Select("SELECT r.name FROM role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);
}
