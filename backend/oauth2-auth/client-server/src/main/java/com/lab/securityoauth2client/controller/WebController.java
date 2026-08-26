package com.lab.securityoauth2client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 客户端资源调用控制器。
 *
 * <p>演示 OAuth2 核心业务逻辑：
 * 客户端用获取到的 Access Token 去调用资源服务器受保护接口。
 * 通过 OAuth2AuthorizedClientService 获取当前用户的 Token，
 * 设置 Authorization: Bearer {token} 请求头。</p>
 */
@RestController
@RequestMapping("/api/oauth2-client")
public class WebController {

    private static final String REGISTRATION_ID = "lab-client";
    private static final String AUTHORIZE_URL = "/oauth2/authorization/lab-client";

    // RestTemplate 由 SecurityConfig 的 @Bean 提供（带超时控制，防资源服务器无响应时无限挂起）
    private final RestTemplate restTemplate;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${demo.resource-server.list-uri}")
    private String resourceListUri;

    public WebController(RestTemplate restTemplate,
                         OAuth2AuthorizedClientService authorizedClientService) {
        this.restTemplate = restTemplate;
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * 用当前用户的 Access Token 调用资源服务器。
     *
     * <p>流程：
     * ① 检查认证类型（必须是 OAuth2AuthenticationToken）
     * ② OAuth2AuthorizedClientService.loadAuthorizedClient() 获取 Token
     * ③ 设置 Authorization: Bearer {token} 请求头
     * ④ RestTemplate 调用资源服务器</p>
     */
    @GetMapping("/resource/list")
    public Map<String, Object> resourceList(Authentication authentication) {
        // ① 必须是通过 OAuth2 认证的（表单登录没有 access token 可用）
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            return authorizationRequired("当前还没有完成 OAuth2 授权", null);
        }

        // ② 获取 Access Token（框架在 OAuth2 登录时自动保存到 AuthorizedClientService）
        OAuth2AuthorizedClient authorizedClient = authorizedClientService
                .loadAuthorizedClient(REGISTRATION_ID, oauth2Token.getName());
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            return authorizationRequired("当前会话中没有可用 access token", oauth2Token.getPrincipal());
        }

        try {
            // ③ 构建请求（Bearer Token）— 这就是 OAuth2 客户端调用资源服务器的标准方式
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue());

            // ④ 调资源服务器（resource-server 的受保护接口）
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    resourceListUri, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("items", response.getBody());
            return result;
        } catch (HttpClientErrorException.Forbidden ex) {
            // 403：token 有效但 scope/权限不足 → 引导用户重新授权
            return authorizationRequired("access token 没有读取列表的权限", oauth2Token.getPrincipal());
        } catch (HttpClientErrorException.Unauthorized ex) {
            // 401：token 已过期/失效 → 引导用户重新授权
            return authorizationRequired("access token 已失效", oauth2Token.getPrincipal());
        } catch (Exception ex) {
            // 其他异常（网络、序列化等）→ 返回错误信息
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", "查询失败：" + ex.getMessage());
            return result;
        }
    }

    /** 构建"需要授权"的统一响应（前端据此展示重新授权按钮） */
    private Map<String, Object> authorizationRequired(String message, OAuth2User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("authRequired", true);
        result.put("message", message);
        result.put("authorizeUrl", AUTHORIZE_URL);  // 前端点击 → 重新走 OAuth2 授权流程
        result.put("clientUser", user != null ? user.getAttributes() : new HashMap<>());
        result.put("items", new ArrayList<>());
        return result;
    }
}
