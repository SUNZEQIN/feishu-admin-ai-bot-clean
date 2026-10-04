package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 按角色分级的工具权限服务。
 *
 * <p>作用：在工具真正执行前，把「角色 + 工具」的判定补上。</p>
 *
 * <p>关键点：电商工具是网关式调用——LLM 只输出 `ecommerce.call_tool`，
 * 真正的业务工具名藏在参数 `toolName` 里。所以校验必须深入参数，
 * 只看顶层工具名会让「查客户明细」绕过分级。</p>
 *
 * @author sunzeqin
 */
@Service
public class RoleToolPermissionService {

    // 当前服务使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(RoleToolPermissionService.class);

    // 不参与电商分级矩阵的工具：飞书侧动作与工具清单查询。
    private static final Set<String> ROLE_FREE_TOOLS = Set.of(
            "cli.run_skill",
            "feishu.scope_for_domain",
            "ecommerce.list_tools");

    // 电商工具网关，业务工具名在参数里。
    private static final String ECOMMERCE_GATEWAY = "ecommerce.call_tool";

    /**
     * 校验一次工具调用在当前角色下是否允许。
     *
     * @param call 工具调用
     * @param role 调用者角色
     * @return 判定结果
     */
    public RoleToolPolicy.Decision check(ToolCall call, BotRole role) {
        // 空调用直接拒绝，避免绕过校验。
        if (call == null) {
            return RoleToolPolicy.Decision.deny(ToolErrorCode.PERMISSION_DENIED, "工具调用为空");
        }

        // 读取顶层工具名。
        String name = call.name();

        // 工具名为空无法判定，按拒绝处理。
        if (name == null || name.isBlank()) {
            return RoleToolPolicy.Decision.deny(ToolErrorCode.PERMISSION_DENIED, "工具名称为空");
        }

        // 飞书侧工具与清单查询不受电商分级影响，由原有白名单机制负责。
        if (ROLE_FREE_TOOLS.contains(name)) {
            return RoleToolPolicy.Decision.allow();
        }

        // 电商网关：必须按参数里的真实工具名判定。
        if (ECOMMERCE_GATEWAY.equals(name)) {
            // 取出真实电商工具名。
            String innerTool = param(call, "toolName");

            // 走权限矩阵。
            RoleToolPolicy.Decision decision = RoleToolPolicy.decide(role, innerTool);

            // 拒绝时打印证据日志，方便审计和排查。
            if (!decision.allowed()) {
                log.warn("[阶段5 权限分级] 拒绝：角色={}，电商工具={}，错误码={}，原因={}",
                        role, innerTool, decision.errorCode(), decision.internalNote());
            }

            // 返回矩阵判定结果。
            return decision;
        }

        // 其它工具不在本服务职责内，交给上层原有机制，这里不额外放行也不额外拒绝。
        return RoleToolPolicy.Decision.allow();
    }

    private static String param(ToolCall call, String key) {
        // 从参数里取值。
        Object value = call.params().get(key);

        // 空值返回空字符串。
        return value == null ? "" : value.toString();
    }
}
