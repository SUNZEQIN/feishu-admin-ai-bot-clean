package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.ChatMember;
import com.sunzeqin.feishuadmin.pojo.FeishuApplication;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.FeishuOpenApiService;
import com.sunzeqin.feishuadmin.service.cli.SkillCliExecutorService;
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

    // Skill + CLI 执行服务，固定工具不满足时由它兜底。
    private final SkillCliExecutorService skillCliExecutor;

    public ToolRegistryService(FeishuOpenApiService openApi, SkillCliExecutorService skillCliExecutor) {
        // 保存飞书 OpenAPI 服务。
        this.openApi = openApi;

        // 保存 Skill + CLI 执行服务。
        this.skillCliExecutor = skillCliExecutor;
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

                2. application.list_installed_apps
                   作用：查询当前提问用户可用的应用列表。
                   参数：openId。
                   openId 必须使用当前消息发送人的 open_id，由系统自动注入，LLM 不需要猜。
                   返回：applications，每个应用包含 appId 和 appName。
                   用途：当用户要拉机器人进群时，用机器人名称匹配 appName，拿到 cli_ 开头的 appId。

                3. im.create_chat
                   作用：创建群聊。
                   参数：chatName, userOpenIds, botAppIds。
                   userOpenIds 是用户 open_id 列表；botAppIds 是机器人 app_id 列表。

                4. cli.run_skill
                   作用：当固定工具无法完成需求时，使用本地 Skill + lark-cli 执行长尾飞书能力。
                   参数：domain, goal, sourceChatId。
                   domain 只能是 im、base、docs、calendar、vc、contact、approval。
                   goal 是用户原始目标的完整中文描述。
                   sourceChatId 是当前飞书事件所在群或会话 ID。
                   注意：只有固定工具不满足时才使用这个工具；固定工具能完成时不要调用它。
                """;
    }

    public ToolResult execute(ToolCall call) {
        // 打印工具调用入参，方便排查 Agent 到底让系统做了什么。
        log.info("工具调用开始：工具名称={}，入参={}", call.name(), call.params());

        try {
            // 根据工具名称分发到具体执行方法。
            if ("im.list_chat_members".equals(call.name())) {
                ToolResult result = listChatMembers(call);
                log.info("工具调用结果：工具名称={}，是否成功={}，说明={}，数据={}",
                        result.tool(), result.success(), result.message(), result.data());
                return result;
            }

            // 根据工具名称分发到查询企业安装应用工具。
            if ("application.list_installed_apps".equals(call.name())) {
                ToolResult result = listInstalledApplications(call);
                log.info("工具调用结果：工具名称={}，是否成功={}，说明={}，数据={}",
                        result.tool(), result.success(), result.message(), result.data());
                return result;
            }

            // 根据工具名称分发到创建群聊工具。
            if ("im.create_chat".equals(call.name())) {
                ToolResult result = createChat(call);
                log.info("工具调用结果：工具名称={}，是否成功={}，说明={}，数据={}",
                        result.tool(), result.success(), result.message(), result.data());
                return result;
            }

            // 根据工具名称分发到 Skill + CLI 兜底工具。
            if ("cli.run_skill".equals(call.name())) {
                ToolResult result = runSkill(call);
                log.info("工具调用结果：工具名称={}，是否成功={}，说明={}，数据={}",
                        result.tool(), result.success(), result.message(), result.data());
                return result;
            }

            // 不认识的工具直接失败，防止模型编造工具。
            ToolResult result = ToolResult.failed(call.name(), "未知工具：" + call.name());
            log.info("工具调用结果：工具名称={}，是否成功={}，说明={}，数据={}",
                    result.tool(), result.success(), result.message(), result.data());
            return result;
        } catch (Exception e) {
            // 工具执行异常时，返回失败结果给 Agent 观察。
            log.warn("工具调用异常：工具名称={}，错误={}", call.name(), e.getMessage());
            ToolResult result = ToolResult.failed(call.name(), e.getMessage());
            log.info("工具调用结果：工具名称={}，是否成功={}，说明={}，数据={}",
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
            log.warn("工具参数已修正：工具名称={}，字段=memberIdType，原值=app_id，新值=open_id，原因=飞书接口不支持app_id",
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

    private ToolResult listInstalledApplications(ToolCall call) {
        // 从参数里读取提问用户 open_id。
        String openId = stringParam(call, "openId");

        // open_id 不能为空，因为飞书接口要按用户查询可用应用。
        if (openId.isBlank()) {
            return ToolResult.failed(call.name(), "application.list_installed_apps 缺少 openId 参数");
        }

        // 调飞书接口查询当前提问用户可用应用列表。
        List<FeishuApplication> applications = openApi.listInstalledApplications(openId);

        // 把应用对象转成简单 Map，方便 LLM 在 observation 里阅读。
        List<Map<String, Object>> applicationMaps = new ArrayList<>();

        // 遍历应用列表。
        for (FeishuApplication application : applications) {
            // 保存应用关键字段。
            applicationMaps.add(Map.of(
                    "appId", application.appId(),
                    "appName", application.appName()
            ));
        }

        // 返回工具成功结果。
        return ToolResult.success(call.name(), "查询当前用户可用应用成功", Map.of(
                "openId", openId,
                "applications", applicationMaps
        ));
    }

    private ToolResult runSkill(ToolCall call) {
        // 从参数里读取业务域。
        String domain = stringParam(call, "domain");

        // 从参数里读取用户目标。
        String goal = stringParam(call, "goal");

        // 从参数里读取来源会话 ID。
        String sourceChatId = stringParam(call, "sourceChatId");

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
        Map<String, Object> data = skillCliExecutor.runSkill(domain, goal, sourceChatId);

        // 返回执行结果。
        return ToolResult.success(call.name(), "Skill + CLI 执行完成", data);
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
