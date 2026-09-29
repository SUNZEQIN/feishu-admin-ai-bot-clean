package com.sunzeqin.feishuadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 飞书应用配置。
 *
 * <p>作用：统一承载 OpenAPI 地址、应用凭据、事件校验 token 和处理中回复文案。</p>
 *
 * @author sunzeqin
 */
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {
    // 飞书 OpenAPI 基础地址，默认使用飞书官方地址。
    private String baseUrl = "https://open.feishu.cn";

    // 飞书应用 app_id，用于获取 tenant_access_token。
    private String appId = "";

    // 飞书应用 app_secret，用于获取 tenant_access_token。
    private String appSecret = "";

    // 飞书事件订阅里的 verification token，用于校验回调来源。
    private String verificationToken = "";

    // 是否在收到消息后先回复“正在处理”，避免用户觉得机器人卡住。
    private boolean processingReplyEnabled = true;

    // 收到消息后的处理中提示文案。
    private String processingReplyText = "⏳ 正在处理，请稍等...";

    // 是否启用 LLM 意图识别；关闭时使用本地规则兜底。
    private boolean llmEnabled = false;

    // OpenAI 兼容模型接口地址，例如 DeepSeek 的 API 地址。
    private String llmBaseUrl = "https://api.deepseek.com";

    // 大模型 API Key。
    private String llmApiKey = "";

    // 大模型名称。
    private String llmModelName = "deepseek-chat";

    // 意图识别温度，默认 0，保证输出稳定。
    private double llmTemperature = 0.0;

    // 是否启用会话记忆。
    private boolean memoryEnabled = true;

    // 每个“会话 + 用户”最多保留多少条记忆消息。
    private int memoryMaxMessages = 20;

    // 是否启用 Skill + CLI 兜底执行器。
    private boolean cliEnabled = true;

    // lark-cli 命令路径，容器里一般就是 lark-cli。
    private String cliCommand = "lark-cli";

    // Skill + CLI 内部最多执行多少轮。
    private int cliMaxSteps = 8;

    // 单条 CLI 命令最大等待秒数。
    private int cliTimeoutSeconds = 60;

    // 允许通过 Skill + CLI 执行的业务域。
    private String cliAllowedDomains = "im,base,docs,calendar,vc,contact,approval";

    public String getBaseUrl() {
        // 返回飞书 OpenAPI 基础地址。
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        // 设置飞书 OpenAPI 基础地址。
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        // 返回飞书应用 app_id。
        return appId;
    }

    public void setAppId(String appId) {
        // 设置飞书应用 app_id。
        this.appId = appId;
    }

    public String getAppSecret() {
        // 返回飞书应用 app_secret。
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        // 设置飞书应用 app_secret。
        this.appSecret = appSecret;
    }

    public String getVerificationToken() {
        // 返回飞书事件 verification token。
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        // 设置飞书事件 verification token。
        this.verificationToken = verificationToken;
    }

    public boolean isProcessingReplyEnabled() {
        // 返回是否开启处理中提示。
        return processingReplyEnabled;
    }

    public void setProcessingReplyEnabled(boolean processingReplyEnabled) {
        // 设置是否开启处理中提示。
        this.processingReplyEnabled = processingReplyEnabled;
    }

    public String getProcessingReplyText() {
        // 返回处理中提示文案。
        return processingReplyText;
    }

    public void setProcessingReplyText(String processingReplyText) {
        // 设置处理中提示文案。
        this.processingReplyText = processingReplyText;
    }

    public boolean isLlmEnabled() {
        // 返回是否启用 LLM 意图识别。
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        // 设置是否启用 LLM 意图识别。
        this.llmEnabled = llmEnabled;
    }

    public String getLlmBaseUrl() {
        // 返回 OpenAI 兼容接口地址。
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        // 设置 OpenAI 兼容接口地址。
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmApiKey() {
        // 返回大模型 API Key。
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        // 设置大模型 API Key。
        this.llmApiKey = llmApiKey;
    }

    public String getLlmModelName() {
        // 返回大模型名称。
        return llmModelName;
    }

    public void setLlmModelName(String llmModelName) {
        // 设置大模型名称。
        this.llmModelName = llmModelName;
    }

    public double getLlmTemperature() {
        // 返回模型温度。
        return llmTemperature;
    }

    public void setLlmTemperature(double llmTemperature) {
        // 设置模型温度。
        this.llmTemperature = llmTemperature;
    }

    public boolean isMemoryEnabled() {
        // 返回是否启用会话记忆。
        return memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        // 设置是否启用会话记忆。
        this.memoryEnabled = memoryEnabled;
    }

    public int getMemoryMaxMessages() {
        // 返回最大记忆消息条数。
        return memoryMaxMessages;
    }

    public void setMemoryMaxMessages(int memoryMaxMessages) {
        // 设置最大记忆消息条数，最小值保护为 2。
        this.memoryMaxMessages = Math.max(2, memoryMaxMessages);
    }

    public boolean isCliEnabled() {
        // 返回是否启用 Skill + CLI。
        return cliEnabled;
    }

    public void setCliEnabled(boolean cliEnabled) {
        // 设置是否启用 Skill + CLI。
        this.cliEnabled = cliEnabled;
    }

    public String getCliCommand() {
        // 返回 lark-cli 命令路径。
        return cliCommand;
    }

    public void setCliCommand(String cliCommand) {
        // 设置 lark-cli 命令路径。
        this.cliCommand = cliCommand;
    }

    public int getCliMaxSteps() {
        // 返回 CLI 最大执行轮数。
        return cliMaxSteps;
    }

    public void setCliMaxSteps(int cliMaxSteps) {
        // 设置 CLI 最大执行轮数，最小值保护为 1。
        this.cliMaxSteps = Math.max(1, cliMaxSteps);
    }

    public int getCliTimeoutSeconds() {
        // 返回 CLI 单命令超时时间。
        return cliTimeoutSeconds;
    }

    public void setCliTimeoutSeconds(int cliTimeoutSeconds) {
        // 设置 CLI 单命令超时时间，最小值保护为 5 秒。
        this.cliTimeoutSeconds = Math.max(5, cliTimeoutSeconds);
    }

    public String getCliAllowedDomains() {
        // 返回允许的 CLI 业务域。
        return cliAllowedDomains;
    }

    public void setCliAllowedDomains(String cliAllowedDomains) {
        // 设置允许的 CLI 业务域。
        this.cliAllowedDomains = cliAllowedDomains;
    }
}
