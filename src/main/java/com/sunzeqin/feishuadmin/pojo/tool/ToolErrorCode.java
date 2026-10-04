package com.sunzeqin.feishuadmin.pojo.tool;

/**
 * 工具与任务的失败错误码。
 *
 * <p>作用：把「失败」变成机器可分类、用户可看懂的两层信息。</p>
 *
 * <ul>
 *     <li>错误码：写进审计表，用来统计和排查；</li>
 *     <li>用户话术：直接发给用户，必须是定稿文案，不能现场发挥。</li>
 * </ul>
 *
 * <p>文案来源：产品需求 `docs/prd-ecommerce-v1.md` 第 4.5 节「失败话术」。</p>
 *
 * @author sunzeqin
 */
public enum ToolErrorCode {

    // 权限不足：不告诉用户"数据存在但你不能看"，只给开通路径。
    PERMISSION_DENIED("这条数据需要运营负责人权限。需要我帮你把开通需求发给管理员吗？"),

    // V1 明确不做的动作：建群、批量通知、写回电商系统。
    NOT_SUPPORTED_IN_V1("这个操作 V1 还不支持，我们会放到下一版再做。"),

    // 电商系统不可用或超时：明确说明不会给估算值。
    MCP_UNAVAILABLE("电商系统暂时不可用，请稍后再试。本次没有取到数据，我不会给估算值。"),

    // 查询无结果。
    EMPTY_RESULT("没有查到符合条件的数据。"),

    // 意图识别失败。
    INTENT_UNKNOWN("没听懂要查什么。可以这样说：查低库存商品 / 查某客户的订单 / 做活动复盘。"),

    // 排队已满。
    QUEUE_FULL("当前请求较多，请 1 分钟后再试。"),

    // 部分成功，需要把已完成和未完成的分开说。
    PARTIAL("已完成：A。未完成：B（原因）。"),

    // 需要用户确认才能继续的写动作。
    NEED_CONFIRM("将要执行：X。回复「确认执行」继续，其他回复视为取消。"),

    // 未分类的内部异常：不把堆栈抛给用户，也不能假装成功。
    INTERNAL_ERROR("系统处理出错，本次没有完成任务。请稍后再试或联系管理员。");

    // 用户可见的定稿文案。
    private final String reply;

    ToolErrorCode(String reply) {
        // 保存用户可见文案。
        this.reply = reply;
    }

    /**
     * 用户可见文案。
     *
     * @return 定稿话术
     */
    public String reply() {
        // 返回定稿话术。
        return reply;
    }
}
