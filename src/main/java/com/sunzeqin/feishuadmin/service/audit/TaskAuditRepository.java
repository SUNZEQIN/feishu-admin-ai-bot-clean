package com.sunzeqin.feishuadmin.service.audit;

import com.sunzeqin.feishuadmin.pojo.audit.AgentTaskRecord;
import com.sunzeqin.feishuadmin.pojo.audit.AgentToolCallRecord;

/**
 * 任务审计仓库。
 *
 * <p>作用：把任务与工具调用事实落库，供排查和试点复盘使用。</p>
 *
 * @author sunzeqin
 */
public interface TaskAuditRepository {

    /**
     * 新建一条任务记录，状态为 RUNNING。
     *
     * @param record 任务记录
     */
    void insertTask(AgentTaskRecord record);

    /**
     * 结束任务，写入最终状态、阶段与错误信息。
     *
     * @param taskId    任务 ID
     * @param status    最终状态
     * @param stage     结束阶段说明
     * @param errorCode 错误码，可为空字符串
     * @param errorMsg  错误信息，可为空字符串
     */
    void finishTask(String taskId, String status, String stage, String errorCode, String errorMsg);

    /**
     * 补齐意图域，只在当前为空时写入，保证第一次生效。
     *
     * @param taskId       任务 ID
     * @param intentDomain 意图域
     */
    void updateIntentDomain(String taskId, String intentDomain);

    /**
     * 记录一次工具调用。
     *
     * @param record 工具调用记录
     */
    void insertToolCall(AgentToolCallRecord record);
}
