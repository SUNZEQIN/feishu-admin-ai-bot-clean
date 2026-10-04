package com.sunzeqin.feishuadmin.pojo.role;

/**
 * 机器人使用者角色。
 *
 * <p>作用：把「谁能看什么数据」这件事变成代码里的固定等级，而不是靠提示词或模型判断。</p>
 *
 * <p>等级含义：</p>
 * <ul>
 *     <li>L1 普通成员：只看聚合与脱敏数据；</li>
 *     <li>L2 运营负责人：可看客户明细与订单；</li>
 *     <li>L3 管理员 / IT：可查审计数据、维护角色名单。</li>
 * </ul>
 *
 * <p>默认值永远是 L1：查不到角色、角色名写错、数据库异常，都按最小权限处理。</p>
 *
 * @author sunzeqin
 */
public enum BotRole {

    // 普通成员：客服、仓配、财务等，只看聚合与脱敏数据。
    L1(1),

    // 运营负责人：可以看客户明细与订单。
    L2(2),

    // 管理员 / IT：可以查审计数据、调整角色名单。
    L3(3);

    // 等级数值，用来做「至少达到某级别」的比较。
    private final int level;

    BotRole(int level) {
        // 保存等级数值。
        this.level = level;
    }

    /**
     * 等级数值。
     *
     * @return 1/2/3
     */
    public int level() {
        // 返回等级数值。
        return level;
    }

    /**
     * 默认角色。
     *
     * @return 永远返回 L1，保证「未登记 = 最小权限」
     */
    public static BotRole defaultRole() {
        // 未登记或识别失败时一律按 L1 处理。
        return L1;
    }

    /**
     * 把数据库里的原始字符串转成角色。
     *
     * <p>无法识别的一律回落到 L1，绝不因为写错一个字符串就把人升权。</p>
     *
     * @param raw 角色原始值，例如 L1、l2、null
     * @return 角色，识别失败返回 L1
     */
    public static BotRole of(String raw) {
        // 空值直接按默认角色处理。
        if (raw == null || raw.isBlank()) {
            return defaultRole();
        }

        // 去空格并转大写，方便数据库里写 l1 / L1 都能识别。
        String value = raw.trim().toUpperCase();

        // 逐个比对枚举名。
        for (BotRole role : values()) {
            if (role.name().equals(value)) {
                return role;
            }
        }

        // 无法识别时拒绝升权，按最小权限处理。
        return defaultRole();
    }

    /**
     * 判断当前角色是否至少达到某个级别。
     *
     * @param other 目标级别
     * @return true 表示当前角色等级大于等于目标级别
     */
    public boolean atLeast(BotRole other) {
        // 目标为空时视为不满足，避免空指针和意外放行。
        return other != null && this.level >= other.level;
    }
}
