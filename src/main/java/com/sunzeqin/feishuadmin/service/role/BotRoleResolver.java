package com.sunzeqin.feishuadmin.service.role;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 角色解析服务。
 *
 * <p>作用：把「一条消息的发送人」翻译成角色等级，并且保证任何异常都不会意外升权。</p>
 *
 * <p>设计取舍：这里刻意做成「宽容失败」——数据库连不上时不是报错退出，而是按 L1 继续服务。
 * 理由是：宁可让运营负责人这一次看到权限不足的提示，也不能因为异常把普通成员当成管理员。</p>
 *
 * @author sunzeqin
 */
@Service
public class BotRoleResolver {

    // 当前服务使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(BotRoleResolver.class);

    // 角色仓库，负责真实查库。
    private final BotRoleRepository repository;

    public BotRoleResolver(BotRoleRepository repository) {
        // 保存角色仓库。
        this.repository = repository;
    }

    /**
     * 解析发送人角色。
     *
     * @param openId 飞书用户 open_id
     * @return 角色；未登记、身份为空或查库失败时一律返回 L1
     */
    public BotRole resolve(String openId) {
        // 身份为空时无法查库，按最小权限处理。
        if (openId == null || openId.isBlank()) {
            // 打印脱敏日志，方便排查"为什么权限不对"。
            log.warn("[阶段5 权限分级] 调用者身份为空，按最小权限 L1 处理");
            return BotRole.defaultRole();
        }

        try {
            // 查库拿角色原始值。
            String raw = repository.findRole(openId);

            // 原始值转枚举，识别失败会回落到 L1。
            BotRole role = BotRole.of(raw);

            // 打印脱敏日志：只留前 6 位，避免完整 open_id 刷进日志。
            log.info("[阶段5 权限分级] 角色解析完成：调用者={}，角色={}，来源={}",
                    mask(openId), role, raw == null || raw.isBlank() ? "未登记默认L1" : "角色表");

            // 返回解析结果。
            return role;
        } catch (Exception e) {
            // 查库异常时不阻断业务，但必须落下证据。
            log.warn("[阶段5 权限分级] 查询角色失败，按最小权限 L1 处理：调用者={}，错误={}",
                    mask(openId), e.getMessage());

            // 异常兜底：最小权限。
            return BotRole.defaultRole();
        }
    }

    /**
     * 身份脱敏，日志里不出现完整 open_id。
     *
     * @param id 飞书身份 ID
     * @return 脱敏后的字符串
     */
    private static String mask(String id) {
        // 空值返回占位符。
        if (id == null || id.isBlank()) {
            return "未知";
        }

        // 太短直接整体脱敏。
        if (id.length() <= 6) {
            return "***";
        }

        // 保留前 6 位方便对照排查。
        return id.substring(0, 6) + "***";
    }
}
