package com.sunzeqin.feishuadmin.pojo.tool;

import java.util.Map;

/**
 * Agent 要执行的一次工具调用。
 *
 * <p>作用：保存 LLM 规划出来的工具名称和工具参数，Java 执行器只认这个结构。</p>
 *
 * @param name   工具名称，例如 cli.run_skill 或 ecommerce.call_tool
 * @param params 工具参数
 *
 * @author sunzeqin
 */
public record ToolCall(String name, Map<String, Object> params) {

    public ToolCall {
        // name 为空时转成空字符串，避免日志出现 null。
        name = name == null ? "" : name;

        // params 为空时转成空 Map，避免工具执行时空指针。
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
