package com.sunzeqin.feishuadmin.utils;

/**
 * 大模型错误工具类。
 *
 * <p>作用：把大模型 SDK 抛出的技术异常转换成用户能看懂的中文提示。</p>
 *
 * @author sunzeqin
 */
public final class LlmErrorUtils {

    // 私有构造方法，避免工具类被实例化。
    private LlmErrorUtils() {
    }

    /**
     * 判断是否是大模型余额不足。
     *
     * @param error 异常对象
     * @return true 表示余额不足
     */
    public static boolean insufficientBalance(Throwable error) {
        // 异常为空时直接返回 false。
        if (error == null) {
            return false;
        }

        // 读取完整异常链文本。
        String text = fullErrorText(error);

        // 按常见返回文本判断余额不足。
        return text.contains("Insufficient Balance")
                || text.contains("insufficient balance")
                || text.contains("余额不足");
    }

    /**
     * 生成余额不足时给用户看的提示。
     *
     * @return 中文提示
     */
    public static String insufficientBalanceReply() {
        return "⚠️ 大模型余额不足，当前请求没有继续执行。\n\n"
                + "🔎 原因：LLM 服务返回 Insufficient Balance。\n\n"
                + "请管理员给当前大模型账号充值，或者在服务端 `.env` 里更换一个有余额的 `FEISHU_LLM_API_KEY`，然后重启服务。";
    }

    /**
     * 读取异常链文本。
     *
     * @param error 异常对象
     * @return 异常文本
     */
    public static String fullErrorText(Throwable error) {
        // 保存异常文本。
        StringBuilder builder = new StringBuilder();

        // 从当前异常一路读取 cause。
        Throwable current = error;
        while (current != null) {
            // 追加异常类名。
            builder.append(current.getClass().getName()).append(": ");

            // 追加异常消息。
            if (current.getMessage() != null) {
                builder.append(current.getMessage());
            }

            // 换行分隔。
            builder.append('\n');

            // 继续读取下一层 cause。
            current = current.getCause();
        }

        // 返回完整异常文本。
        return builder.toString();
    }
}
