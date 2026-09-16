package com.lab.securityoauth2authserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 登录页面控制器。
 *
 * <p>当 {@code formLogin().loginPage("/api/oauth2-auth/login")} 设置为自定义路径时，
 * Spring Security 不会自动生成登录页面，需要应用自己提供。
 * GET 展示页面（本控制器），POST 认证处理由 UsernamePasswordAuthenticationFilter 接管。</p>
 */
@Controller
public class LoginController {

    /** auth-server 独立入口：根路径 / 与 /api/oauth2-auth 重定向到客户端管理页（未登录会先跳登录）。
     *  背景：登录成功后如果没有 saved-request，Spring Security 默认会跳 /；但 / 不是后端端点会 404。
     *  这里把根路径统一指到客户端管理页，保证 auth-server 单独打开时始终能进入可用页面。 */
    @GetMapping({"/", "/api/oauth2-auth"})
    public String index() {
        return "redirect:/api/oauth2-auth/clients";
    }
    @GetMapping("/api/oauth2-auth/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误，请重试");
        }
        if (logout != null) {
            model.addAttribute("message", "您已成功退出登录");
        }
        return "login";     // Thymeleaf 模板名 → templates/login.html
    }
}
