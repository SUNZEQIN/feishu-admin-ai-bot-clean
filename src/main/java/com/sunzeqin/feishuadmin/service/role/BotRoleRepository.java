package com.sunzeqin.feishuadmin.service.role;

/**
 * 机器人使用者角色仓库。
 *
 * <p>作用：角色名单只存在数据库里，由管理员维护；模型无权决定谁是 L2。</p>
 *
 * @author sunzeqin
 */
public interface BotRoleRepository {

    /**
     * 查询某个飞书用户的角色原始值。
     *
     * @param openId 飞书用户 open_id
     * @return 角色原始字符串，例如 L2；没有登记时返回 null
     */
    String findRole(String openId);
}
