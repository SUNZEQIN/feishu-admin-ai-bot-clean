package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.ChatMember;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.FeishuOpenApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    // 飞书 OpenAPI 服务，实际接口调用由它完成。
    private final FeishuOpenApiService openApi;

    public ToolRegistryService(FeishuOpenApiService openApi) {
        // 保存飞书 OpenAPI 服务。
        this.openApi = openApi;
    }

    public String toolDescriptions() {
        // 返回给 LLM 看的工具清单，LLM 只能从这些工具里选择下一步。
        return """
                可用工具：
                1. im.list_chat_members
                   作用：查询群成员。
                   参数：chatId, memberIdType。
                   memberIdType 只能是 open_id、user_id、union_id。
                   注意：飞书查询群成员接口不支持 app_id，不要传 app_id。
                   一般默认使用 open_id，然后根据返回的 memberType/bot 字段区分用户和机器人。

                2. im.create_chat
                   作用：创建群聊。
                   参数：chatName, userOpenIds, botAppIds。
                   userOpenIds 是用户 open_id 列表；botAppIds 是机器人 app_id 列表。
                """;
    }

    public ToolResult execute(ToolCall call) {
        // 打印工具调用入参，方便排查 Agent 到底让系统做了什么。
        log.info("TOOL_CALL name={} params={}", call.name(), call.params());

        try {
            // 根据工具名称分发到具体执行方法。
            if ("im.list_chat_members".equals(call.name())) {
                ToolResult result = listChatMembers(call);
                log.info("TOOL_CALL_RESULT name={} success={} message={} data={}",
                        result.tool(), result.success(), result.message(), result.data());
                return result;
            }

            // 根据工具名称分发到创建群聊工具。
            if ("im.create_chat".equals(call.name())) {
                ToolResult result = createChat(call);
                log.info("TOOL_CALL_RESULT name={} success={} message={} data={}",
                        result.tool(), result.success(), result.message(), result.data());
                return result;
            }

            // 不认识的工具直接失败，防止模型编造工具。
            ToolResult result = ToolResult.failed(call.name(), "未知工具：" + call.name());
            log.info("TOOL_CALL_RESULT name={} success={} message={} data={}",
                    result.tool(), result.success(), result.message(), result.data());
            return result;
        } catch (Exception e) {
            // 工具执行异常时，返回失败结果给 Agent 观察。
            log.warn("TOOL_CALL_FAILED name={} error={}", call.name(), e.getMessage());
            ToolResult result = ToolResult.failed(call.name(), e.getMessage());
            log.info("TOOL_CALL_RESULT name={} success={} message={} data={}",
                    result.tool(), result.success(), result.message(), result.data());
            return result;
        }
    }

    private ToolResult listChatMembers(ToolCall call) {
        // 从参数里读取群 chat_id。
        String chatId = stringParam(call, "chatId");

        // 从参数里读取 memberIdType。
        String memberIdType = stringParam(call, "memberIdType");

        // memberIdType 为空时默认查 open_id。
        if (memberIdType.isBlank()) {
            memberIdType = "open_id";
        }

        // 飞书这个接口不支持 app_id；如果 LLM 传错了，这里强制改成 open_id，避免接口 400。
        if ("app_id".equals(memberIdType)) {
            log.warn("TOOL_PARAM_FIXED tool={} field=memberIdType oldValue=app_id newValue=open_id reason=feishu_api_not_support_app_id",
                    call.name());
            memberIdType = "open_id";
        }

        // 调飞书接口查询群成员。
        List<ChatMember> members = openApi.listChatMembers(chatId, memberIdType);

        // 把成员对象转成简单 Map，方便 LLM 在 observation 里阅读。
        List<Map<String, Object>> memberMaps = new ArrayList<>();

        // 遍历成员列表。
        for (ChatMember member : members) {
            // 保存成员关键字段。
            memberMaps.add(Map.of(
                    "memberId", member.memberId(),
                    "name", member.name(),
                    "memberType", member.memberType(),
                    "idType", member.idType(),
                    "bot", member.bot()
            ));
        }

        // 返回工具成功结果。
        return ToolResult.success(call.name(), "查询群成员成功", Map.of(
                "chatId", chatId,
                "memberIdType", memberIdType,
                "members", memberMaps
        ));
    }

    private ToolResult createChat(ToolCall call) {
        // 从参数里读取新群名称。
        String chatName = stringParam(call, "chatName");

        // 读取用户 open_id 列表。
        List<String> userOpenIds = stringListParam(call, "userOpenIds");

        // 读取机器人 app_id 列表。
        List<String> botAppIds = stringListParam(call, "botAppIds");

        // 调飞书接口创建群聊。
        String chatId = openApi.createChat(chatName, userOpenIds, botAppIds);

        // 返回创建结果。
        return ToolResult.success(call.name(), "创建群聊成功", Map.of(
                "chatName", chatName,
                "chatId", chatId,
                "userOpenIds", userOpenIds,
                "botAppIds", botAppIds
        ));
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

    private List<String> stringListParam(ToolCall call, String key) {
        // 从参数 Map 里取值。
        Object value = call.params().get(key);

        // 保存最终字符串列表。
        List<String> result = new ArrayList<>();

        // 如果参数本身就是列表，就逐个转成字符串。
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
        }

        // 返回字符串列表。
        return result;
    }
}
