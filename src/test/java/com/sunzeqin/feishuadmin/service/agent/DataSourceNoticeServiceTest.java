package com.sunzeqin.feishuadmin.service.agent;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据来源标注测试（产品规则 C-08）。
 *
 * <p>用途：V1 用的是测试数据，回复必须标注来源，否则会被当成真实经营数字。</p>
 *
 * @author sunzeqin
 */
class DataSourceNoticeServiceTest {

    @Test
    void appendsNoticeWhenEcommerceDataWasUsed() {
        // 有一次成功的电商调用 → 必须标注来源。
        DataSourceNoticeService service = new DataSourceNoticeService(new FeishuProperties());

        String reply = service.apply("销售额最高的是洗衣液。", List.of(
                ToolResult.success("ecommerce.call_tool", "调用完成", Map.of("rows", "x"))));

        assertTrue(reply.startsWith("销售额最高的是洗衣液。"));
        assertTrue(reply.contains(DataSourceNoticeService.NOTICE_TEXT));
    }

    @Test
    void appendsNoticeOnlyOnce() {
        // 已经标注过就不能重复追加。
        DataSourceNoticeService service = new DataSourceNoticeService(new FeishuProperties());

        String reply = service.apply("结论。\n\n" + DataSourceNoticeService.NOTICE_TEXT, List.of(
                ToolResult.success("ecommerce.call_tool", "调用完成", Map.of())));

        assertEquals(1, reply.split(DataSourceNoticeService.NOTICE_TEXT, -1).length - 1);
    }

    @Test
    void keepsReplyUnchangedWithoutEcommerceData() {
        // 纯飞书操作不贴电商来源标签。
        DataSourceNoticeService service = new DataSourceNoticeService(new FeishuProperties());

        String reply = service.apply("已把文档发给你。", List.of(
                ToolResult.success("cli.run_skill", "执行完成", Map.of())));

        assertEquals("已把文档发给你。", reply);
    }

    @Test
    void keepsReplyUnchangedWhenEcommerceCallFailed() {
        // 失败的调用没有数据，不能标注（避免暗示"数据可信"）。
        DataSourceNoticeService service = new DataSourceNoticeService(new FeishuProperties());

        String reply = service.apply("电商系统暂时不可用。", List.of(
                ToolResult.failed("ecommerce.call_tool", "超时")));

        assertEquals("电商系统暂时不可用。", reply);
    }

    @Test
    void respectsDisabledSwitch() {
        // 开关关闭时不做任何加工。
        FeishuProperties properties = new FeishuProperties();
        properties.setEcommerceDataSourceNoticeEnabled(false);

        DataSourceNoticeService service = new DataSourceNoticeService(properties);

        String reply = service.apply("结论。", List.of(
                ToolResult.success("ecommerce.call_tool", "调用完成", Map.of())));

        assertEquals("结论。", reply);
    }

    @Test
    void handlesNullReplyAndNullObservations() {
        // 空回复不抛异常。
        DataSourceNoticeService service = new DataSourceNoticeService(new FeishuProperties());

        assertNull(service.apply(null, List.of(ToolResult.success("ecommerce.call_tool", "ok", Map.of()))));
        assertEquals("结论。", service.apply("结论。", null));
    }
}
