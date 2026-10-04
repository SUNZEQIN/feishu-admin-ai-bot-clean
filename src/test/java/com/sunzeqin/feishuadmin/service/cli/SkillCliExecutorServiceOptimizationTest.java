package com.sunzeqin.feishuadmin.service.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.cli.CliCommandResult;
import com.sunzeqin.feishuadmin.service.FeishuOpenApiService;
import com.sunzeqin.feishuadmin.service.FeishuUserScopeMappingService;
import com.sunzeqin.feishuadmin.service.UserOAuthTokenService;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Skill + CLI 执行器的性能优化测试。
 */
class SkillCliExecutorServiceOptimizationTest {

    @Test
    void topNCreateTimeSearchUsesOnlyRequestedPageSize() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod(
                "normalizeCommand", List.class, String.class, String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> normalized = (List<String>) method.invoke(service,
                List.of("lark-cli", "drive", "+search", "--doc-types", "bitable", "--mine",
                        "--sort", "create_time", "--page-size", "20", "--as", "user", "--format", "json"),
                "删除我名下创建时间最早的10个多维表格",
                "base",
                "ou_test");

        assertEquals("10", normalized.get(normalized.indexOf("--page-size") + 1),
                "只需要最早10个时，搜索不应先拉20个再翻页");
    }

    @Test
    void promptUsesCompactObservationInsteadOfFullCliJson() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod(
                "buildPrompt", String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        String hugeJson = "{\"items\":[" + "{\"name\":\"表格\",\"token\":\"tok\"},".repeat(500) + "]}";
        String prompt = (String) method.invoke(service,
                "base",
                "删除我名下创建时间最早的10个多维表格",
                "oc_test",
                "om_test",
                "ou_test",
                "user_test",
                "skill text",
                List.of(new CliCommandResult("lark-cli drive +search --format json", 0, hugeJson, "")));

        assertFalse(prompt.contains(hugeJson), "规划提示词不能回灌完整搜索 JSON，否则每轮都会越来越慢");
        assertTrue(prompt.contains("stdout长度"), "提示词仍要保留可供模型判断的结果摘要");
    }

    @Test
    void querySignatureIgnoresOutputProjectionFlags() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod("commandSignature", List.class);
        method.setAccessible(true);

        // 同一条搜索的基础部分，只差最后的输出投影写法。
        List<String> base = List.of("lark-cli", "drive", "+search", "--doc-types", "bitable",
                "--mine", "--as", "user", "--page-size", "10", "--sort", "create_time");

        String first = (String) method.invoke(service, concat(base,
                List.of("--format", "json", "--jq", ".data.results | map({token: .result_meta.token})")));
        String second = (String) method.invoke(service, concat(base,
                List.of("--json", "--jq", "{items: .data.results, has_more: .data.has_more}")));

        assertEquals(first, second,
                "只改 --jq / --format 的重复查询必须算出同一个签名，否则会绕过重复熔断继续空转");

        // 换了过滤条件就是另一条查询，不能被误判成重复。
        String otherQuery = (String) method.invoke(service, concat(
                List.of("lark-cli", "drive", "+search", "--doc-types", "sheet",
                        "--mine", "--as", "user", "--page-size", "10", "--sort", "create_time"),
                List.of("--format", "json")));

        assertFalse(first.equals(otherQuery), "换了过滤条件的查询不能被误判成重复");
    }

    @Test
    void latestObservationKeepsMoreDataThanOlderOnes() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod(
                "buildPrompt", String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        // 用纯字母标记，避免被 JSON 转义破坏。
        String older = "B".repeat(3000);
        String latest = "A".repeat(3000);

        String prompt = (String) method.invoke(service,
                "base",
                "删除我名下创建时间最早的10个多维表格",
                "oc_test",
                "om_test",
                "ou_test",
                "user_test",
                "skill text",
                List.of(
                        new CliCommandResult("lark-cli drive +search --format json", 0, older, ""),
                        new CliCommandResult("lark-cli drive +search --format json", 0, latest, "")));

        assertTrue(prompt.contains(latest),
                "最新一条 observation 必须完整回灌，否则模型看不见完整结果就会反复重查同一条命令");
        assertFalse(prompt.contains(older), "更早的 observation 仍要压缩，避免提示词无限膨胀");
        assertTrue(prompt.contains("B".repeat(800)), "更早的 observation 至少要保留摘要");
    }

    @Test
    void writeCommandsAreRecognisedSoRepeatedReadsStillAllowedAfterThem() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod("isWriteCommand", List.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(service,
                        List.of("lark-cli", "drive", "+delete", "--file-token", "tok", "--type", "bitable")),
                "删除是写操作：写完必须允许重新查询，否则「创建/删除后再确认」的正常流程会被误拦");
        assertTrue((Boolean) method.invoke(service,
                        List.of("lark-cli", "im", "+messages-reply", "--message-id", "om_x", "--text", "hi")),
                "发送消息是写操作");
        assertFalse((Boolean) method.invoke(service,
                        List.of("lark-cli", "drive", "+search", "--doc-types", "bitable", "--mine")),
                "搜索是读操作，重复查询必须被拦住");
        assertFalse((Boolean) method.invoke(service, List.of("lark-cli", "base", "--help")),
                "help 是只读元命令");
    }

    private List<String> concat(List<String> head, List<String> tail) {
        List<String> all = new ArrayList<>(head);
        all.addAll(tail);
        return all;
    }

    private SkillCliExecutorService service() {
        FeishuProperties properties = new FeishuProperties();
        properties.setCliCommand("lark-cli");
        properties.setCliAllowedDomains("im,base,drive,wiki");
        properties.setLlmEnabled(false);
        return new SkillCliExecutorService(
                properties,
                new JsonUtils(new ObjectMapper()),
                mock(FeishuOpenApiService.class),
                mock(UserOAuthTokenService.class),
                mock(FeishuUserScopeMappingService.class),
                new DestructiveCommandGuard());
    }
}
