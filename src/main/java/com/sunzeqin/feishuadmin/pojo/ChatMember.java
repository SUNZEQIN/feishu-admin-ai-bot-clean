package com.sunzeqin.feishuadmin.pojo;

/**
 * 飞书群成员。
 *
 * <p>作用：统一描述用户成员和机器人成员。用户通常用 open_id，机器人通常用 app_id。</p>
 *
 * @param memberId   成员 ID，用户为 open_id，机器人为 app_id
 * @param name       成员名称
 * @param memberType 成员类型
 * @param idType     memberId 的类型，例如 open_id 或 app_id
 * @param tenantKey  租户标识
 *
 * @author sunzeqin
 */
public record ChatMember(String memberId, String name, String memberType, String idType, String tenantKey) {

    /**
     * 判断当前成员是否为机器人。
     *
     * @return true 表示机器人
     */
    public boolean bot() {
        // memberType 可能为空，先转成安全字符串再判断。
        String type = memberType == null ? "" : memberType.toLowerCase();

        // 如果 ID 类型是 app_id，或者成员类型里包含 bot/app，就认为是机器人。
        return "app_id".equals(idType) || type.contains("bot") || type.contains("app");
    }
}
