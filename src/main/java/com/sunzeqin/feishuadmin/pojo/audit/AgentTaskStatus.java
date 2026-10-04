package com.sunzeqin.feishuadmin.pojo.audit;

/**
 * 任务生命周期状态。
 *
 * <p>作用：让 `agent_task.status` 与真实结果一致，避免"失败了却记成功"。
 * 来源：产品规则 O-02。</p>
 *
 * @author sunzeqin
 */
public enum AgentTaskStatus {

    // 已接收、正在处理。
    RUNNING,

    // 全流程成功。
    SUCCESS,

    // 全流程失败，没有得到任何可用结果。
    FAILED,

    // 部分成功：有的步骤拿到了结果，后面的步骤失败。
    PARTIAL,

    // 高风险操作已被拦下，等待用户原话确认。
    WAITING_CONFIRM
}
