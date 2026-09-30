package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.EcommerceMcpClientService;
import com.sunzeqin.feishuadmin.service.FeishuUserScopeMappingService;
import com.sunzeqin.feishuadmin.service.cli.SkillCliExecutorService;
import com.sunzeqin.feishuadmin.utils.LlmErrorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工具注册与执行服务。
 *
 * <p>作用：集中管理 Agent 可以调用的工具。LLM 只输出工具名和参数，
 * 真实执行统一在这里完成，避免模型直接碰飞书 OpenAPI。</p>
 *
 * @author sunzeqin
 */
@Service
public class ToolRegistryService {
    // 当前工具注册表使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);

    // Skill + CLI 执行服务，飞书相关能力统一由它处理。
    private final SkillCliExecutorService skillCliExecutor;

    // 电商 MCP 客户端服务，负责调用独立电商项目。
    private final EcommerceMcpClientService ecommerceMcpClient;

    // 飞书用户身份 scope 映射服务，负责查询业务域需要的授权范围。
    private final FeishuUserScopeMappingService scopeMappingService;

    public ToolRegistryService(SkillCliExecutorService skillCliExecutor, EcommerceMcpClientService ecommerceMcpClient,
            FeishuUserScopeMappingService scopeMappingService) {
        // 保存 Skill + CLI 执行服务。
        this.skillCliExecutor = skillCliExecutor;

        // 保存电商 MCP 客户端服务。
        this.ecommerceMcpClient = ecommerceMcpClient;

        // 保存用户身份 scope 映射服务。
        this.scopeMappingService = scopeMappingService;
    }

    public String toolDescriptions() {
        // 返回给 LLM 看的工具清单，LLM 只能从这些工具里选择下一步。
        return """
                可用工具：
                1. cli.run_skill
                   作用：统一执行飞书相关能力，包含群聊、消息、文档、多维表格、日程、会议、审批、通讯录等。
                   参数：domain, goal, sourceChatId, originalMessageId, senderOpenId, senderUserId。
                   domain 只能是 im、base、docs、calendar、vc、minutes、note、contact、approval、attendance、drive、wiki、markdown、mindnotes、whiteboard。
                   goal 是用户原始目标的完整中文描述。
                   sourceChatId 是当前飞书事件所在群或会话 ID。
                   originalMessageId 是用户原消息 ID，发卡片或消息时优先引用这条原文。
                   senderOpenId 是触发人的 open_id，群聊回复时优先 @ 这个人。
                   注意：飞书内部操作都走这个工具，不要再调用固定 OpenAPI 工具。

                2. feishu.scope_for_domain
                   作用：查询某个飞书业务域在用户身份下需要申请哪些 OAuth scope。
                   参数：domain。
                   domain 例如 im、base、docs、calendar、vc、contact、approval、attendance、drive、wiki、minutes。
                   用途：当用户明确要求“用本人身份 / 以用户身份”执行飞书操作时，可先查询对应模块 scope。

                3. ecommerce.list_tools
                   作用：查询电商 MCP 服务可用工具。
                   参数：无。
                   用途：当用户提出电商业务需求，但你不确定具体工具名时，先调用它。

                4. ecommerce.call_tool
                   作用：调用电商 MCP 服务里的具体业务工具。
                   参数：toolName, arguments。
                   toolName 例如 ecommerce.query_top_products、ecommerce.query_low_inventory、ecommerce.query_customer_orders。
                   arguments 是电商工具入参，例如 limit、threshold、customerName、months。
                   注意：电商数据分析、订单、商品、库存、退款、客户画像、活动复盘需求，应优先调用这个工具拿真实结构化数据。
                """;
    }

    public ToolResult execute(ToolCall call) {
        // 打印工具调用入参，方便排查 Agent 到底让系统做了什么。
        log.info("[阶段4 工具调用] 开始：工具名称={}，入参={}", call.name(), call.params());

        try {
            // 根据工具名称分发到 Skill + CLI 飞书统一入口。
            if ("cli.run_skill".equals(call.name())) {
                ToolResult result = runSkill(call);
                logResult(result);
                return result;
            }

            // 根据工具名称分发到飞书用户身份 scope 映射查询。
            if ("feishu.scope_for_domain".equals(call.name())) {
                ToolResult result = scopeForDomain(call);
                logResult(result);
                return result;
            }

            // 根据工具名称分发到电商 MCP 工具列表。
            if ("ecommerce.list_tools".equals(call.name())) {
                ToolResult result = listEcommerceTools(call);
                logResult(result);
                return result;
            }

            // 根据工具名称分发到电商 MCP 工具调用。
            if ("ecommerce.call_tool".equals(call.name())) {
                ToolResult result = callEcommerceTool(call);
                logResult(result);
                return result;
            }

            // 不认识的工具直接失败，防止模型编造工具。
            ToolResult result = ToolResult.failed(call.name(), "未知工具：" + call.name());
            logResult(result);
            return result;
        } catch (Exception e) {
            // 工具执行异常时，返回失败结果给 Agent 观察。
            log.warn("[阶段4 工具调用] 异常：工具名称={}，错误={}", call.name(), e.getMessage());

            // 大模型余额不足时返回中文业务提示，不把底层 JSON 直接抛给用户。
            String message = e.getMessage();
            if (LlmErrorUtils.insufficientBalance(e)) {
                message = LlmErrorUtils.insufficientBalanceReply();
            }

            // 构造失败结果。
            ToolResult result = ToolResult.failed(call.name(), message);
            logResult(result);
            return result;
        }
    }

    private void logResult(ToolResult result) {
        // INFO 只打印结果摘要，完整数据放到 DEBUG，避免一屏日志被大 JSON 淹没。
        log.info("[阶段4 工具调用] 结果摘要：工具名称={}，是否成功={}，说明={}，数据字段={}",
                result.tool(), result.success(), result.message(), result.data().keySet());
        log.debug("[阶段4 工具调用] 结果完整数据：工具名称={}，数据={}", result.tool(), result.data());
    }

    private ToolResult runSkill(ToolCall call) {
        // 从参数里读取业务域。
        String domain = stringParam(call, "domain");

        // 从参数里读取用户目标。
        String goal = stringParam(call, "goal");

        // 从参数里读取来源会话 ID。
        String sourceChatId = stringParam(call, "sourceChatId");

        // 从参数里读取原消息 ID。
        String originalMessageId = stringParam(call, "originalMessageId");

        // 从参数里读取发送人 open_id。
        String senderOpenId = stringParam(call, "senderOpenId");

        // 从参数里读取发送人 user_id。
        String senderUserId = stringParam(call, "senderUserId");

        // CLI 业务域不能为空。
        if (domain.isBlank()) {
            return ToolResult.failed(call.name(), "cli.run_skill 缺少 domain 参数");
        }

        // 用户目标不能为空。
        if (goal.isBlank()) {
            return ToolResult.failed(call.name(), "cli.run_skill 缺少 goal 参数");
        }

        // 来源会话不能为空，Skill 里“本群/当前群”都要靠它解析。
        if (sourceChatId.isBlank()) {
            return ToolResult.failed(call.name(), "cli.run_skill 缺少 sourceChatId 参数");
        }

        // 调用 Skill + CLI 执行器。
        Map<String, Object> data = skillCliExecutor.runSkill(domain, goal, sourceChatId,
                originalMessageId, senderOpenId, senderUserId);

        // 返回执行结果。
        return ToolResult.success(call.name(), "Skill + CLI 执行完成", data);
    }

    private ToolResult listEcommerceTools(ToolCall call) {
        // 调用电商 MCP 查询工具列表。
        Map<String, Object> data = ecommerceMcpClient.listTools();

        // 返回工具列表结果。
        return ToolResult.success(call.name(), "查询电商 MCP 工具列表成功", data);
    }

    private ToolResult scopeForDomain(ToolCall call) {
        // 从参数里读取业务域。
        String domain = stringParam(call, "domain");

        // 业务域不能为空。
        if (domain.isBlank()) {
            return ToolResult.failed(call.name(), "feishu.scope_for_domain 缺少 domain 参数");
        }

        // 查询业务域对应的用户身份 scope。
        Map<String, Object> data = scopeMappingService.scopeToolResult(domain);

        // 返回查询结果。
        return ToolResult.success(call.name(), "查询飞书用户身份scope成功", data);
    }

    private ToolResult callEcommerceTool(ToolCall call) {
        // 读取电商工具名称。
        String toolName = stringParam(call, "toolName");

        // 工具名称不能为空。
        if (toolName.isBlank()) {
            return ToolResult.failed(call.name(), "ecommerce.call_tool 缺少 toolName 参数");
        }

        // 读取电商工具参数。
        Map<String, Object> arguments = mapParam(call, "arguments");

        // 调用电商 MCP 工具。
        Map<String, Object> data = ecommerceMcpClient.callTool(toolName, arguments);

        // 返回调用结果。
        return ToolResult.success(call.name(), "调用电商 MCP 工具完成", data);
    }

    private String stringParam(ToolCall call, String key) {
        // 从参数 Map 里取值。
        Object value = call.params().get(key);

        // 空值返回空字符串。
        if (value == null) {
            return "";
        }

        // 其它值统一转成字符串。
        return value.toString();
    }

    private Map<String, Object> mapParam(ToolCall call, String key) {
        // 从参数 Map 里取值。
        Object value = call.params().get(key);

        // 如果参数本身就是 Map，就逐项转成字符串 key。
        if (value instanceof Map<?, ?> map) {
            java.util.HashMap<String, Object> result = new java.util.HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }

        // 不存在时返回空 Map。
        return Map.of();
    }
}
