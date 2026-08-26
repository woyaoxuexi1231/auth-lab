package com.lab.securityoauth2authserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.securityoauth2authserver.dto.ClientCreateForm;
import com.lab.securityoauth2authserver.entity.OAuth2Client;
import com.lab.securityoauth2authserver.mapper.OAuth2ClientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 客户端管理页面 — 列表 / 注册 / 编辑 / 删除。
 *
 * <p>路径挂在 /api/oauth2-auth/clients 下（经网关可访问），安全配置要求 ROLE_ADMIN（见
 * DefaultSecurityConfig）。</p>
 *
 * <p>注册与编辑共用 {@link ClientCreateForm} 和 {@link #buildRegisteredClient}：
 * <ul>
 *   <li><b>注册</b>（POST /clients）：密钥必填，生成新 UUID 作为 RegisteredClient.id。</li>
 *   <li><b>编辑</b>（GET /clients/{id}/edit 回显 + POST /clients/{id} 保存）：
 *       <b>RegisteredClient.id 保持不变</b>（它是授权上下文的关联键，变了会丢弃已签发的
 *       authorization/consent）；client_secret 留空时保留原 BCrypt 密文，不重新编码，
 *       避免"编辑一次就改密码"的意外。</li>
 * </ul>
 */
@Controller
@RequestMapping("/api/oauth2-auth/clients")
@RequiredArgsConstructor
public class ClientAdminController {

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2ClientMapper clientMapper;
    private final PasswordEncoder passwordEncoder;

    /** 默认回调地址（来自 application-{profile}.yml 的 app.oauth2.redirect-uri），注册表单预填用 */
    @Value("${app.oauth2.redirect-uri:}")
    private String defaultRedirectUri;

    /** 列表 */
    @GetMapping
    public String list(Model model) {
        List<OAuth2Client> clients = clientMapper.selectList(null);  // 全量查询客户端（学习项目数据量小，无分页）
        model.addAttribute("clients", clients);
        model.addAttribute("defaultRedirectUri", defaultRedirectUri);  // 供模板预填回调地址
        return "clients";  // Thymeleaf 模板
    }

    /** 编辑页：按 id 回显现有客户端（同一个 clients.html，进入编辑模式） */
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable("id") String id, Model model) {
        OAuth2Client client = clientMapper.selectById(id);
        if (client == null) {
            return "redirect:/api/oauth2-auth/clients";  // 客户端不存在 → 回列表
        }
        model.addAttribute("clients", clientMapper.selectList(null));
        model.addAttribute("defaultRedirectUri", defaultRedirectUri);
        model.addAttribute("editing", client);  // 被编辑对象 → 模板显示表单回显
        // 把逗号分隔的多值字段拆成集合，供模板 checkbox 回显（勾选当前值）
        model.addAttribute("editingGrantTypes", splitValues(client.getAuthorizationGrantTypes()));
        model.addAttribute("editingAuthMethods", splitValues(client.getClientAuthenticationMethods()));
        return "clients";
    }

    /** 注册新客户端 */
    @PostMapping
    public String create(ClientCreateForm form, RedirectAttributes ra) {
        // ① 共用校验（必填项 + 授权类型 + 认证方式）
        String error = validate(form);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/api/oauth2-auth/clients";
        }
        // ② 注册场景密钥必填（编辑可留空）
        if (!StringUtils.hasText(form.getClientSecret())) {
            ra.addFlashAttribute("error", "客户端密钥为必填");
            return "redirect:/api/oauth2-auth/clients";
        }
        // ③ 业务唯一键校验（client_id 不允许重复）
        if (clientIdExists(form.getClientId(), null)) {
            ra.addFlashAttribute("error", "客户端ID已存在: " + form.getClientId());
            return "redirect:/api/oauth2-auth/clients";
        }
        // 注册：新 UUID 作为 RegisteredClient.id（与 client_id 解耦）
        RegisteredClient client = buildRegisteredClient(UUID.randomUUID().toString(), form,
                passwordEncoder.encode(form.getClientSecret()));  // 密钥 BCrypt 加密后入库
        registeredClientRepository.save(client);  // 委托 DB 仓库持久化
        ra.addFlashAttribute("message", "客户端注册成功: " + form.getClientId());
        return "redirect:/api/oauth2-auth/clients";
    }

    /** 更新客户端（RegisteredClient.id 保持不变） */
    @PostMapping("/{id}")
    public String update(@PathVariable("id") String id, ClientCreateForm form, RedirectAttributes ra) {
        // ① 目标必须存在
        OAuth2Client existing = clientMapper.selectById(id);
        if (existing == null) {
            ra.addFlashAttribute("error", "客户端不存在或已被删除");
            return "redirect:/api/oauth2-auth/clients";
        }
        // ② 共用校验失败 → 回编辑页保留已填内容
        String error = validate(form);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/api/oauth2-auth/clients/" + id + "/edit";
        }
        // ③ client_id 冲突校验（排除自身）
        if (clientIdExists(form.getClientId(), id)) {
            ra.addFlashAttribute("error", "客户端ID已存在: " + form.getClientId());
            return "redirect:/api/oauth2-auth/clients/" + id + "/edit";
        }
        // 密钥：留空 => 保留原 BCrypt 密文；填写 => 重新加密更新
        String secret = StringUtils.hasText(form.getClientSecret())
                ? passwordEncoder.encode(form.getClientSecret())
                : existing.getClientSecret();
        RegisteredClient client = buildRegisteredClient(existing.getId(), form, secret);  // 沿用原 id
        registeredClientRepository.save(client);
        ra.addFlashAttribute("message", "客户端已更新: " + form.getClientId());
        return "redirect:/api/oauth2-auth/clients";
    }

    /** 删除客户端 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") String id, RedirectAttributes ra) {
        if (clientMapper.deleteById(id) > 0) {
            ra.addFlashAttribute("message", "已删除客户端");
        } else {
            ra.addFlashAttribute("error", "客户端不存在或已删除");
        }
        return "redirect:/api/oauth2-auth/clients";
    }

    // ==================== 共用逻辑 ====================

    /** 注册/编辑共用的必填校验（密钥单独校验，因为编辑时密钥可留空） */
    private String validate(ClientCreateForm form) {
        if (!StringUtils.hasText(form.getClientId())
                || !StringUtils.hasText(form.getClientName())
                || !StringUtils.hasText(form.getRedirectUris())
                || !StringUtils.hasText(form.getScopes())) {
            return "客户端ID、名称、回调地址、scope 均为必填";
        }
        if (form.getGrantTypes() == null || form.getGrantTypes().length == 0) {
            return "至少选择一种授权类型";
        }
        if (form.getAuthMethods() == null || form.getAuthMethods().length == 0) {
            return "至少选择一种客户端认证方式";
        }
        return null;  // 校验通过
    }

    /** clientId 是否已存在；excludeId 用于编辑时排除自身（null 表示不排除） */
    private boolean clientIdExists(String clientId, String excludeId) {
        LambdaQueryWrapper<OAuth2Client> wrapper = new LambdaQueryWrapper<OAuth2Client>()
                .eq(OAuth2Client::getClientId, clientId);
        if (excludeId != null) {
            wrapper.ne(OAuth2Client::getId, excludeId);  // 编辑时排除自己，否则"没改 clientId 也会判重"
        }
        Long count = clientMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /** 用表单构建 RegisteredClient（注册与编辑共用） */
    private RegisteredClient buildRegisteredClient(String id, ClientCreateForm form, String secret) {
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(form.getClientId())
                .clientName(form.getClientName())
                .clientAuthenticationMethods(methods -> Arrays.stream(form.getAuthMethods())
                        .forEach(m -> methods.add(new ClientAuthenticationMethod(m))))  // 认证方式多选
                .authorizationGrantTypes(grants -> Arrays.stream(form.getGrantTypes())
                        .forEach(g -> grants.add(new AuthorizationGrantType(g))))        // 授权类型多选
                .redirectUris(uris -> splitLines(form.getRedirectUris()).forEach(uris::add))  // 回调地址（可多行/逗号）
                .scopes(scopes -> splitLines(form.getScopes()).forEach(scopes::add))          // scope 白名单
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(form.isRequireProofKey())                  // 要求 PKCE
                        .requireAuthorizationConsent(form.isRequireAuthorizationConsent())  // 要求授权确认
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(ttl(form.getAccessTokenTtlSeconds(), 3600)))    // 默认 1 小时
                        .refreshTokenTimeToLive(Duration.ofSeconds(ttl(form.getRefreshTokenTtlSeconds(), 604800))) // 默认 7 天
                        .reuseRefreshTokens(form.isReuseRefreshTokens())
                        .build());
        if (StringUtils.hasText(secret)) {
            builder.clientSecret(secret);  // 只有填了才设置密钥（编辑留空时沿用旧密文）
        }
        return builder.build();
    }

    /** 按换行或逗号拆分成多个值，去空去重（回调地址 / scope 用） */
    private static Set<String> splitLines(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.hasText(value)) {
            Arrays.stream(value.split("\\s*(?:\\r?\\n|,)\\s*"))  // 换行或逗号分隔
                    .filter(StringUtils::hasText)                 // 去空
                    .forEach(result::add);                        // LinkedHashSet 天然去重
        }
        return result;
    }

    /** 按逗号拆分（编辑回显 checkbox 用） */
    private static Set<String> splitValues(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(Arrays.asList(value.split("\\s*,\\s*")));
    }

    /** TTL 兜底：null 或非正数 → 默认值 */
    private static long ttl(Integer value, int def) {
        return value == null || value <= 0 ? def : value;
    }
}
