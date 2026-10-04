package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电商工具角色权限矩阵测试。
 *
 * <p>用例编号对应验收用例：EC-01 ~ EC-05（正常路径）、EC-11 ~ EC-16（权限与越权）。</p>
 *
 * @author sunzeqin
 */
class RoleToolPolicyTest {

    @Test
    void allowsAggregateToolsForL1() {
        // L1 可以看聚合数据：TOP 商品、低库存、活动复盘、退款排行。
        assertTrue(RoleToolPolicy.decide(BotRole.L1, "ecommerce.query_top_products").allowed());
        assertTrue(RoleToolPolicy.decide(BotRole.L1, "ecommerce.query_low_inventory").allowed());
        assertTrue(RoleToolPolicy.decide(BotRole.L1, "ecommerce.query_activity_report").allowed());
        assertTrue(RoleToolPolicy.decide(BotRole.L1, "ecommerce.query_refund_top_products").allowed());
    }

    @Test
    void deniesCustomerOrdersForL1WithPermissionScript() {
        // EC-11：L1 查客户明细必须被拒，并且只能给权限引导话术。
        RoleToolPolicy.Decision decision = RoleToolPolicy.decide(BotRole.L1, "ecommerce.query_customer_orders");

        assertFalse(decision.allowed());
        assertEquals(ToolErrorCode.PERMISSION_DENIED, decision.errorCode());
        assertTrue(decision.userMessage().contains("运营负责人权限"));
    }

    @Test
    void allowsCustomerOrdersForL2AndL3() {
        // EC-03 / EC-15：L2、L3 可以查客户明细。
        assertTrue(RoleToolPolicy.decide(BotRole.L2, "ecommerce.query_customer_orders").allowed());
        assertTrue(RoleToolPolicy.decide(BotRole.L3, "ecommerce.query_customer_orders").allowed());
    }

    @Test
    void treatsNullRoleAsL1() {
        // 角色漏传时必须按最小权限处理，不能默认放行。
        assertFalse(RoleToolPolicy.decide(null, "ecommerce.query_customer_orders").allowed());
        assertTrue(RoleToolPolicy.decide(null, "ecommerce.query_top_products").allowed());
    }

    @Test
    void deniesV2WriteActionsForEveryRole() {
        // EC-16：建群、批量通知、写回电商系统在 V1 一律不支持。
        for (BotRole role : BotRole.values()) {
            assertEquals(ToolErrorCode.NOT_SUPPORTED_IN_V1,
                    RoleToolPolicy.decide(role, "ecommerce.create_group").errorCode());
            assertEquals(ToolErrorCode.NOT_SUPPORTED_IN_V1,
                    RoleToolPolicy.decide(role, "ecommerce.batch_notify").errorCode());
            assertEquals(ToolErrorCode.NOT_SUPPORTED_IN_V1,
                    RoleToolPolicy.decide(role, "ecommerce.update_price").errorCode());
        }
    }

    @Test
    void deniesUnknownToolInsteadOfSilentlyAllowing() {
        // EC-12：模型编出来的工具名不能因为"没规定"就放行。
        RoleToolPolicy.Decision decision = RoleToolPolicy.decide(BotRole.L3, "ecommerce.dump_all_customers");

        assertFalse(decision.allowed());
        assertEquals(ToolErrorCode.PERMISSION_DENIED, decision.errorCode());
    }

    @Test
    void deniesBlankToolName() {
        // 缺少工具名属于非法调用。
        assertFalse(RoleToolPolicy.decide(BotRole.L3, "   ").allowed());
        assertFalse(RoleToolPolicy.decide(BotRole.L3, null).allowed());
    }

    @Test
    void recognizesKnownTools() {
        // 清单识别用于启动自检：矩阵里有的工具才算已知。
        assertTrue(RoleToolPolicy.known("ecommerce.query_low_inventory"));
        assertTrue(RoleToolPolicy.known("ecommerce.create_group"));
        assertFalse(RoleToolPolicy.known("ecommerce.query_profit"));
    }
}
