package com.sunzeqin.feishuadmin.service.role;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基于 MySQL 的角色仓库实现。
 *
 * <p>表结构见 `src/main/resources/db/schema-mysql.sql` 里的 `bot_user_role`。</p>
 *
 * @author sunzeqin
 */
@Repository
public class JdbcBotRoleRepository implements BotRoleRepository {

    // JDBC 模板，用于查询角色表。
    private final JdbcTemplate jdbcTemplate;

    public JdbcBotRoleRepository(JdbcTemplate jdbcTemplate) {
        // 保存 JDBC 模板。
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String findRole(String openId) {
        // open_id 为空时不去查库，直接当作未登记。
        if (openId == null || openId.isBlank()) {
            return null;
        }

        // 按 open_id 查角色，表上有唯一索引，最多返回一行。
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM bot_user_role WHERE open_id = ?", String.class, openId);

        // 没查到返回 null，由上层按默认角色 L1 处理。
        return roles.isEmpty() ? null : roles.get(0);
    }
}
