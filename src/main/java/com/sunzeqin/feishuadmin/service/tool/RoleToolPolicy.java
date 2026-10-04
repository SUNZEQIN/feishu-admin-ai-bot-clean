package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolErrorCode;

import java.util.Set;

/**
 * 电商工具的角色权限矩阵。
 *
 * <p>作用：把产品需求里的权限矩阵写成一张代码里的表，作为唯一的判定入口。</p>
 *
 * <p>为什么一定要放在 Java 层：模型只会输出一串工具名，它不知道谁是 L1。
 * 只要判定不在代码里，就等于把越权交给了模型。</p>
 *
 * <p>矩阵来源：产品需求 `docs/prd-ecommerce-v1.md` 第 4.1 节。</p>
 *
 * @author sunzeqin
 */
public final class RoleToolPolicy {

    // L1 允许调用的电商只读工具：都是聚合结果，不含客户明细。
    private static final Set<String> L1_TOOLS = Set.of(
            "ecommerce.query_top_products",
            "ecommerce.query_low_inventory",
            "ecommerce.query_activity_report",
            "ecommerce.query_refund_top_products");

    // 只有 L2 及以上才能调用的客户明细工具。
    private static final Set<String> L2_TOOLS = Set.of(
            "ecommerce.query_customer_orders");

    // V1 明确不做的动作：建群、批量通知、写回电商系统。
    private static final Set<String> V1_DISABLED_TOOLS = Set.of(
            "ecommerce.create_group",
            "ecommerce.batch_notify",
            "ecommerce.update_price",
            "ecommerce.update_inventory",
            "ecommerce.ship_order");

    private RoleToolPolicy() {
        // 工具类不允许实例化。
    }

    /**
     * 判断某个角色能否调用某个电商工具。
     *
     * @param role     使用者角色，null 时按 L1 处理
     * @param toolName 电商工具名，例如 ecommerce.query_customer_orders
     * @return 判定结果，拒绝时带上错误码和用户话术
     */
    public static Decision decide(BotRole role, String toolName) {
        // 角色为空时按最小权限处理，避免调用方漏传导致放行。
        BotRole effective = role == null ? BotRole.defaultRole() : role;

        // 工具名做一次清洗。
        String name = toolName == null ? "" : toolName.trim();

        // 工具名为空说明模型没给参数，直接拒绝。
        if (name.isEmpty()) {
            return Decision.deny(ToolErrorCode.PERMISSION_DENIED, "电商工具名为空，已拒绝执行");
        }

        // V1 明确不做的动作：所有角色都拒绝，并明确告诉用户这是版本不支持。
        if (V1_DISABLED_TOOLS.contains(name)) {
            return Decision.deny(ToolErrorCode.NOT_SUPPORTED_IN_V1, "V1 不做该写动作：" + name);
        }

        // L1 可用的聚合工具：所有角色都放行。
        if (L1_TOOLS.contains(name)) {
            return Decision.allow();
        }

        // 客户明细工具：只有 L2 及以上放行。
        if (L2_TOOLS.contains(name)) {
            if (effective.atLeast(BotRole.L2)) {
                return Decision.allow();
            }
            return Decision.deny(ToolErrorCode.PERMISSION_DENIED,
                    "角色 " + effective + " 无权调用客户明细工具：" + name);
        }

        // 不在矩阵内的工具名（模型编的或还没接的工具）一律拒绝，避免隐性放行。
        return Decision.deny(ToolErrorCode.PERMISSION_DENIED, "电商工具不在允许清单内：" + name);
    }

    /**
     * 判断工具名是否在本矩阵清单内。
     *
     * @param toolName 电商工具名
     * @return true 表示矩阵里有这个工具
     */
    public static boolean known(String toolName) {
        // 清洗工具名。
        String name = toolName == null ? "" : toolName.trim();

        // 三类清单里任一命中就算已知工具。
        return L1_TOOLS.contains(name) || L2_TOOLS.contains(name) || V1_DISABLED_TOOLS.contains(name);
    }

    /**
     * 角色权限判定结果。
     *
     * @param allowed      是否允许执行
     * @param errorCode    拒绝时的错误码，允许时为 null
     * @param internalNote 拒绝时的内部原因，只写日志不给用户看
     */
    public record Decision(boolean allowed, ToolErrorCode errorCode, String internalNote) {

        static Decision allow() {
            // 允许执行。
            return new Decision(true, null, "");
        }

        static Decision deny(ToolErrorCode errorCode, String internalNote) {
            // 拒绝执行，原因只留日志。
            return new Decision(false, errorCode, internalNote == null ? "" : internalNote);
        }

        /**
         * 用户可见话术。
         *
         * @return 允许时为空字符串，拒绝时为错误码对应的定稿文案
         */
        public String userMessage() {
            // 允许时没有话术。
            if (allowed || errorCode == null) {
                return "";
            }

            // 拒绝时用定稿文案，禁止现场发挥。
            return errorCode.reply();
        }
    }
}
