package com.lab.securityoauth2authserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.securityoauth2authserver.entity.OAuth2Client;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 客户端 Mapper。
 */
@Mapper
public interface OAuth2ClientMapper extends BaseMapper<OAuth2Client> {
}