package com.sunzeqin.feishuadmin.controller;

import com.sunzeqin.feishuadmin.service.UserOAuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 飞书 OAuth 回调控制器。
 *
 * <p>作用：接收飞书用户授权回调，把 code 换成用户 token 并保存到数据库。</p>
 *
 * @author sunzeqin
 */
@RestController
public class FeishuOAuthController {
    // 当前控制器使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(FeishuOAuthController.class);

    // 用户授权服务。
    private final UserOAuthTokenService userOAuthTokenService;

    public FeishuOAuthController(UserOAuthTokenService userOAuthTokenService) {
        // 保存用户授权服务。
        this.userOAuthTokenService = userOAuthTokenService;
    }

    /**
     * 飞书 OAuth 回调入口。
     *
     * @param code  飞书授权码
     * @param state 授权状态随机串
     * @return 页面提示
     */
    @GetMapping(value = "/api/feishu/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state) {
        try {
            // 打印回调摘要，不打印 token。
            log.info("[阶段4 工具调用] 收到飞书OAuth回调：state={}，code是否存在={}",
                    state, code != null && !code.isBlank());

            // 处理授权回调。
            userOAuthTokenService.handleCallback(code, state);

            // 返回成功页面。
            return """
                    <html>
                      <body style="font-family: sans-serif; padding: 24px;">
                        <h2>授权完成</h2>
                        <p>用户授权信息已保存，可以回到飞书继续使用机器人。</p>
                      </body>
                    </html>
                    """;
        } catch (Exception e) {
            // 打印失败日志。
            log.warn("[阶段4 工具调用] 飞书OAuth回调处理失败：state={}，错误={}", state, e.getMessage());

            // 返回失败页面。
            return """
                    <html>
                      <body style="font-family: sans-serif; padding: 24px;">
                        <h2>授权失败</h2>
                        <p>%s</p>
                      </body>
                    </html>
                    """.formatted(escapeHtml(e.getMessage()));
        }
    }

    private String escapeHtml(String text) {
        // 空文本保护。
        if (text == null) {
            return "";
        }

        // 简单转义 HTML 特殊字符。
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
