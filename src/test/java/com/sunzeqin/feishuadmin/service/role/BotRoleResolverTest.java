package com.sunzeqin.feishuadmin.service.role;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 角色解析服务测试。
 *
 * <p>核心断言只有一句：任何异常路径都不允许升权，永远回落到 L1。</p>
 *
 * @author sunzeqin
 */
class BotRoleResolverTest {

    @Test
    void defaultsToL1WhenUserNotRegistered() {
        // EC-14：未登记用户按 L1 处理。
        BotRoleResolver resolver = new BotRoleResolver(openId -> null);

        assertEquals(BotRole.L1, resolver.resolve("ou_unknown"));
    }

    @Test
    void parsesRegisteredRoleIgnoringCase() {
        // 数据库里写 L2 或 l2 都应该识别成 L2。
        assertEquals(BotRole.L2, new BotRoleResolver(openId -> "L2").resolve("ou_ops"));
        assertEquals(BotRole.L2, new BotRoleResolver(openId -> " l2 ").resolve("ou_ops"));
        assertEquals(BotRole.L3, new BotRoleResolver(openId -> "L3").resolve("ou_admin"));
    }

    @Test
    void fallsBackToL1ForUnknownRoleValue() {
        // 角色名写错时不能升权。
        assertEquals(BotRole.L1, new BotRoleResolver(openId -> "root").resolve("ou_ops"));
        assertEquals(BotRole.L1, new BotRoleResolver(openId -> "ADMIN").resolve("ou_ops"));
    }

    @Test
    void fallsBackToL1WhenRepositoryThrows() {
        // 数据库不可用时的兜底：按最小权限继续服务，而不是报错或放行。
        BotRoleResolver resolver = new BotRoleResolver(openId -> {
            throw new IllegalStateException("db down");
        });

        assertEquals(BotRole.L1, resolver.resolve("ou_ops"));
    }

    @Test
    void skipsRepositoryWhenOpenIdIsBlank() {
        // 身份为空时不该查库。
        AtomicBoolean queried = new AtomicBoolean(false);
        BotRoleResolver resolver = new BotRoleResolver(openId -> {
            queried.set(true);
            return "L3";
        });

        assertEquals(BotRole.L1, resolver.resolve(""));
        assertEquals(BotRole.L1, resolver.resolve(null));
        assertFalse(queried.get());
    }
}
