package com.sunzeqin.feishuadmin.service.cli;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 破坏性命令闸门。
 *
 * <p>作用：把「读操作」和「高风险写操作」分开。高风险写操作不允许由模型自行确认，
 * 必须由用户原话明确确认后才放行。</p>
 *
 * <p>为什么需要这道闸门：一次真实事故中，用户在群里说「用我的身份删掉所有多维表格」，
 * 模型自行给 <code>drive +delete</code> 加上了 <code>--yes</code> —— 而 lark-cli 里
 * <code>--yes</code> 的语义正是「confirm high-risk operation」。结果是 4 个真实多维表格
 * 被直接删除，没有经过任何确认，用户收到的回复还只是「超过最大步骤数」。
 * 这里把确认权从模型手里收回到用户手里。</p>
 *
 * <p>判定依据优先用 lark-cli 自己的风险标记（<code>--yes</code>）和子命令名，
 * 而不是用户话术或业务域，避免把正常的读查询也拦下来。</p>
 *
 * @author sunzeqin
 */
@Service
public class DestructiveCommandGuard {

    /** lark-cli 用它表示「确认高风险操作」。模型自己加上这个参数，等于自己给自己签字。 */
    private static final String AUTO_CONFIRM_FLAG = "--yes";

    /** 破坏性动作关键词。lark-cli 的动作都写在子命令名里，例如 +delete、+version-delete。 */
    private static final List<String> DESTRUCTIVE_KEYWORDS = List.of(
            "delete", "remove", "destroy", "drop", "revoke", "clear", "cancel");

    /** 用户明确确认的说法。只有用户原话里出现这些词，才允许执行破坏性命令。 */
    private static final List<String> CONFIRM_PHRASES = List.of(
            "确认删除", "确定删除", "确认删掉", "确定删掉", "确认执行", "确认操作", "我确认");

    /**
     * 判断一次 CLI 调用是否需要用户确认。
     *
     * @param command lark-cli 命令参数列表
     * @param goal    用户目标原话（外层规划器按约定保留用户完整原话）
     * @return 判定结果
     */
    public Decision check(List<String> command, String goal) {
        // 空命令不需要判断。
        if (command == null || command.isEmpty()) {
            return Decision.readOnly();
        }

        // 找出命中破坏性特征的位置。
        String matched = matchDestructive(command);

        // 没有破坏性特征：按只读操作直接放行。
        if (matched == null) {
            return Decision.readOnly();
        }

        // 破坏性操作：只有用户原话明确确认才放行。
        return new Decision(true, userConfirmed(goal), matched);
    }

    /**
     * 找出命令里命中破坏性特征的部分。
     *
     * <p>只扫描子命令名（以 + 开头）和 <code>--yes</code>，不扫描参数值，
     * 避免把文件 token、文本内容里恰好出现的 delete 当成破坏性动作。</p>
     */
    private String matchDestructive(List<String> command) {
        // --yes 本身就是「高风险操作」的声明。
        if (command.contains(AUTO_CONFIRM_FLAG)) {
            return AUTO_CONFIRM_FLAG;
        }

        // 只读元命令一律放行。--help 只打印用法，不会执行任何写操作；
        // 如果连它都拦，模型连「删除命令怎么用」都读不到，用户确认后也会卡在第一步，
        // 而且拦截提示里展示的会是一条 help 命令，看不出真正要删什么。
        if (command.contains("--help") || command.contains("-h")) {
            return null;
        }

        // lark-cli 的命令形状是 lark-cli <domain> +<shortcut>，动作只可能写在 + 子命令名上。
        for (String part : command) {
            if (part == null || !part.startsWith("+")) {
                continue;
            }

            String shortcut = part.substring(1).toLowerCase(Locale.ROOT);
            for (String keyword : DESTRUCTIVE_KEYWORDS) {
                if (shortcut.contains(keyword)) {
                    return part;
                }
            }
        }

        return null;
    }

    /**
     * 判断用户原话里是否出现明确的确认说法。
     */
    private boolean userConfirmed(String goal) {
        // 没有用户原话时一律视为未确认。
        if (goal == null || goal.isBlank()) {
            return false;
        }

        for (String phrase : CONFIRM_PHRASES) {
            if (goal.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 闸门判定结果。
     *
     * @param destructive  是否属于破坏性操作
     * @param confirmed    用户是否已经明确确认
     * @param matchedToken 命中的破坏性特征，用于日志和提示
     */
    public record Decision(boolean destructive, boolean confirmed, String matchedToken) {

        /**
         * 只读操作的判定结果。
         */
        public static Decision readOnly() {
            return new Decision(false, false, "");
        }

        /**
         * 是否需要拦截：是破坏性操作且用户没有确认。
         */
        public boolean blocked() {
            return destructive && !confirmed;
        }
    }
}
