package com.sunzeqin.feishuadmin.service.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 破坏性命令闸门的回归测试。
 *
 * <p>固化一次真实数据事故的修复：用户在群里说「用我的身份删掉所有多维表格」，
 * 模型自行给 <code>drive +delete</code> 加上了 <code>--yes</code>（lark-cli 里它的语义是
 * confirm high-risk operation），于是 4 个真实多维表格被直接删除，没有经过任何确认。</p>
 *
 * @author sunzeqin
 */
class DestructiveCommandGuardTest {

    private final DestructiveCommandGuard guard = new DestructiveCommandGuard();

    @Test
    void blocksDeleteWithoutUserConfirmation() {
        // 事故原样复现：模型自己加了 --yes，用户原话里没有任何确认词。
        List<String> command = List.of("lark-cli", "drive", "+delete",
                "--file-token", "UlYhbb19YaK7CmsYXDWckjifnIh", "--type", "bitable",
                "--as", "user", "--yes", "--format", "json");

        DestructiveCommandGuard.Decision decision = guard.check(command, "用我的身份删掉所有多维表格");

        assertTrue(decision.destructive(), "应识别为破坏性操作");
        assertTrue(decision.blocked(), "用户未确认时必须拦截");
        assertEquals("--yes", decision.matchedToken(), "应命中 lark-cli 自己的高风险标记");
    }

    @Test
    void blocksDeleteEvenWithoutTheAutoConfirmFlag() {
        // 模型不加 --yes 也要拦住：删不删由用户决定，不由模型决定。
        List<String> command = List.of("lark-cli", "drive", "+delete",
                "--file-token", "PpP7bs1E8awzi0sRNWNcKA0hnEP", "--type", "bitable");

        DestructiveCommandGuard.Decision decision = guard.check(command, "删掉我名下的表格");

        assertTrue(decision.blocked(), "缺少 --yes 也要拦截");
        assertEquals("+delete", decision.matchedToken());
    }

    @Test
    void allowsDeleteAfterExplicitUserConfirmation() {
        List<String> command = List.of("lark-cli", "drive", "+delete",
                "--file-token", "UlYhbb19YaK7CmsYXDWckjifnIh", "--type", "bitable", "--yes");

        assertFalse(guard.check(command, "确认删除我名下的所有多维表格").blocked(),
                "用户明确确认后应放行");
        assertFalse(guard.check(command, "确定删除刚才那个多维表格").blocked());
        assertFalse(guard.check(command, "确认执行").blocked());
    }

    @Test
    void coversOtherDestructiveShortcuts() {
        // lark-cli 的其它删除类子命令同样要拦。
        for (String shortcut : List.of("+delete-reply", "+version-delete", "+chat-cancel", "+file-clear")) {
            List<String> command = List.of("lark-cli", "drive", shortcut, "--token", "x");
            assertTrue(guard.check(command, "帮我处理一下").blocked(),
                    shortcut + " 应被拦截");
        }
    }

    @Test
    void doesNotBlockReadOnlyCommands() {
        // 读查询不能被误拦，否则闸门会挡住正常使用。
        assertFalse(guard.check(List.of("lark-cli", "base", "--help"), "查一下我的表格").blocked());
        assertFalse(guard.check(List.of("lark-cli", "drive", "+search",
                "--doc-types", "bitable", "--mine", "--as", "user"), "查询我名下的所有多维表格").blocked());
        assertFalse(guard.check(List.of("lark-cli", "im", "+messages-reply",
                "--message-id", "om_x", "--text", "结果如下"), "把结果发到群里").blocked());
    }

    @Test
    void doesNotTreatFlagOrArgumentValuesAsDestructive() {
        // 参数值里恰好出现 delete 字样时不能误判：只扫描以 + 开头的子命令名。
        List<String> command = List.of("lark-cli", "drive", "+search",
                "--query", "delete-all-report", "--doc-types", "bitable");

        assertFalse(guard.check(command, "搜索文档").destructive(),
                "参数值里的 delete 不应被当成破坏性动作");
    }

    @Test
    void letsHelpThroughSoTheConfirmPromptShowsTheRealCommand() {
        // 端到端验证时发现的过度拦截：模型面对删除任务的第一步是读 +delete 的用法，
        // 如果连 --help 都拦，用户确认后仍会卡在第一步，而且提示里展示的是一条 help 命令。
        List<String> helpCommand = List.of("lark-cli", "drive", "+delete", "--help");

        assertFalse(guard.check(helpCommand, "用我的身份删掉所有多维表格").blocked(),
                "--help 是只读的，不应拦截");
        assertFalse(guard.check(helpCommand, "用我的身份删掉所有多维表格").destructive());

        // 但真正的删除必须照拦。
        List<String> realDelete = List.of("lark-cli", "drive", "+delete",
                "--file-token", "FAKEGATEtoken0001", "--type", "bitable", "--as", "user");
        assertTrue(guard.check(realDelete, "用我的身份删掉所有多维表格").blocked(),
                "真正的删除命令必须拦截，提示里才能看到要删什么");

        // --yes 的优先级高于 --help：显式的高风险标记仍然算破坏性。
        assertTrue(guard.check(List.of("lark-cli", "drive", "+delete", "--help", "--yes"), "删掉表格").destructive(),
                "--yes 的高风险标记不应被 --help 掩盖");
    }

    @Test
    void toleratesEmptyInput() {
        assertFalse(guard.check(List.of(), "删掉所有表格").blocked());
        assertFalse(guard.check(null, "删掉所有表格").blocked());
    }
}
