package com.lab.securityoauth2authserver.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Chrome DevTools 兼容端点。
 *
 * <p>Chrome DevTools 会探测 /.well-known/appspecific/com.chrome.devtools.json，
 * 返回空 JSON {} 避免控制台持续出现 404 干扰调试。</p>
 */
@RestController
public class ChromeDevToolsController {

    @GetMapping(value = "/.well-known/appspecific/com.chrome.devtools.json",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chromeDevToolsConfig() {
        return Map.of();     // 空 JSON {}
    }
}
