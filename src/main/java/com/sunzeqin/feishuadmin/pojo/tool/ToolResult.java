package com.sunzeqin.feishuadmin.pojo.tool;

import java.util.Map;

/**
 * 工具执行结果。
 *
 * <p>作用：把每一步工具的执行结果返回给 Agent，Agent 根据观察结果决定下一步。</p>
 *
 * @param success 是否成功
 * @param tool    工具名称
 * @param message 结果说明
 * @param data    结构化结果数据
 *
 * @author sunzeqin
 */
public record ToolResult(boolean success, String tool, String message, Map<String, Object> data) {

    public ToolResult {
        // tool 为空时转成空字符串。
        tool = tool == null ? "" : tool;

        // message 为空时转成空字符串。
        message = message == null ? "" : message;

        // data 为空时转成空 Map。
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static ToolResult success(String tool, String message, Map<String, Object> data) {
        // 创建成功结果。
        return new ToolResult(true, tool, message, data);
    }

    public static ToolResult failed(String tool, String message) {
        // 创建失败结果。
        return new ToolResult(false, tool, message, Map.of());
    }
}
