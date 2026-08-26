package com.lab.securityoauth2authserver.controller;

import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 授权确认页控制器。
 *
 * <p>当 RegisteredClient 配置了 requireAuthorizationConsent(true) 时，
 * 用户首次授权时必须访问此页面确认。Spring Authorization Server 不会自动生成此页面。</p>
 *
 * <p>业务逻辑：
 * <ol>
 *   <li>加载客户端信息（展示"谁在请求授权"）</li>
 *   <li>分两组展示 scopes：已授权（不可取消）+ 待授权（用户可勾选）</li>
 *   <li>用户提交后由 Spring Authorization Server 处理授权</li>
 * </ol>
 */
@Controller
public class AuthorizationConsentController {

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationConsentService authorizationConsentService;

    public AuthorizationConsentController(RegisteredClientRepository registeredClientRepository,
                                          OAuth2AuthorizationConsentService authorizationConsentService) {
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationConsentService = authorizationConsentService;
    }

    /**
     * 渲染授权确认页 — 在 OAuth2 /authorize 流程中被跳转过来。
     * 请求参数 client_id / scope / state 由 Spring Authorization Server 转发（OAuth2 协议标准参数）。
     */
    @GetMapping("/api/oauth2-auth/consent")
    public String consent(Principal principal, Model model,
                          @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
                          @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
                          @RequestParam(OAuth2ParameterNames.STATE) String state) {
        // ① 加载客户端信息
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);  // 按协议参数 client_id 查客户端
        if (registeredClient == null) {
            throw new IllegalArgumentException("Unknown client: " + clientId);  // 未知客户端直接拒绝（协议要求）
        }

        // ② 检查已有授权 — 区分"已授权"和"待授权"
        // principal.getName() = 当前登录用户；查该用户此前对该客户端的授权记录
        OAuth2AuthorizationConsent currentConsent =
                authorizationConsentService.findById(registeredClient.getId(), principal.getName());
        Set<String> authorizedScopes = currentConsent != null
                ? currentConsent.getScopes()   // 已授权过的 scope 集合
                : Collections.emptySet();      // 首次授权：无历史记录

        // ③ 分类 scopes
        Set<String> scopesToApprove = new LinkedHashSet<>();
        Set<String> previouslyApprovedScopes = new LinkedHashSet<>();
        // 请求的 scope 用空格分隔（OAuth2 协议规范），逐个判断是否已授权过
        for (String requestedScope : StringUtils.delimitedListToStringArray(scope, " ")) {
            if (authorizedScopes.contains(requestedScope)) {
                previouslyApprovedScopes.add(requestedScope);    // 已授权 — 不可取消（灰显）
            } else {
                scopesToApprove.add(requestedScope);              // 待授权 — 用户可勾选
            }
        }

        // ④ 填充模型 → 渲染 consent.html 模板
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", registeredClient.getClientName());  // 展示"谁在请求授权"
        model.addAttribute("state", state);   // state 原样带回（协议防 CSRF 参数，提交时一并返回）
        model.addAttribute("scopes", scopesToApprove);
        model.addAttribute("previouslyApprovedScopes", previouslyApprovedScopes);
        model.addAttribute("principalName", principal.getName());

        return "consent";
    }
}
