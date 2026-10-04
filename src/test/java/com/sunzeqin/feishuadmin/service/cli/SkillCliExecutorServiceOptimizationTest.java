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
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Skill + CLI 执行器的性能优化测试。
 */
class SkillCliExecutorServiceOptimizationTest {

    @Test
    void oldestIntentKeepsPageSizeSoTheModelCanPageToTheEnd() throws Exception {
        // 「最早」要的东西在降序结果的最后一页。把 page-size 压到 N 只会让页数变多，
        // 而且「翻更多页」会和上一条算出同一个签名、被重复闸门误拦。
        List<String> normalized = normalize(List.of("lark-cli", "drive", "+search", "--doc-types", "bitable",
                "--mine", "--sort", "create_time", "--page-size", "20", "--as", "user", "--format", "json"),
                "删除我名下创建时间最早的10个多维表格");

        assertEquals("20", normalized.get(normalized.indexOf("--page-size") + 1),
                "「最早」不能把 page-size 收敛到 N，否则模型拿不到全量、翻页还会被误判成重复查询");
    }

    @Test
    void newestIntentConvergesPageSizeToRequestedN() throws Exception {
        // 「最新」要的东西就在降序结果的第一页，收敛才是安全的。
        List<String> normalized = normalize(List.of("lark-cli", "drive", "+search", "--doc-types", "bitable",
                "--mine", "--sort", "create_time", "--page-size", "20", "--as", "user", "--format", "json"),
                "打开我名下创建时间最新的10个多维表格");

        assertEquals("10", normalized.get(normalized.indexOf("--page-size") + 1),
                "「最新」要的东西就在第一页，收敛 page-size 才能省数据量和上下文");
    }

    @Test
    void sameQueryMayRunTwiceButNotForever() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod(
                "isDuplicateQuery", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Integer> counts = new HashMap<>();

        assertFalse((Boolean) method.invoke(service, counts, "sig"), "没执行过的查询不是重复");

        counts.put("sig", 1);
        assertFalse((Boolean) method.invoke(service, counts, "sig"),
                "允许重跑一次：模型常常漏取字段（例如分页要用的 page_token），需要一次改投影的机会");

        counts.put("sig", 2);
        assertTrue((Boolean) method.invoke(service, counts, "sig"),
                "同一条查询执行到上限后必须判为重复，否则又会回到空转的死循环");
    }

    @SuppressWarnings("unchecked")
    private List<String> normalize(List<String> command, String goal) throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod(
                "normalizeCommand", List.class, String.class, String.class, String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, command, goal, "base", "ou_test");
    }

    @Test
    void topNIntentOnlyMatchesMineWithOldestOrNewest() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod("parseTopNFileIntent", String.class);
        method.setAccessible(true);

        Object deleteOldest = method.invoke(service, "删除我名下创建时间最早的10个多维表格");
        assertEquals(true, field(deleteOldest, "deleteIntent"), "「删除」要识别成删除意图");
        assertEquals(true, field(deleteOldest, "oldest"), "「最早」要识别成取最早");
        assertEquals(10, field(deleteOldest, "count"), "数量要解析出来");
        assertEquals(false, field(deleteOldest, "createdByMe"), "「我名下」是拥有语义，不是创建语义");

        Object listNewest = method.invoke(service, "列出我创建的最新3个多维表格");
        assertEquals(false, field(listNewest, "deleteIntent"), "只列出时不是删除意图");
        assertEquals(false, field(listNewest, "oldest"), "「最新」要识别成取最新");
        assertEquals(3, field(listNewest, "count"), "数量要解析出来");
        assertEquals(true, field(listNewest, "createdByMe"), "「我创建」是创建语义");

        assertEquals(null, method.invoke(service, "删除多维表格里的第三行记录"), "不是文件级任务，不能接管");
        assertEquals(null, method.invoke(service, "帮我建一个多维表格"), "没有「最早/最新」，不能接管");
        assertEquals(null, method.invoke(service, "删除我名下最早的1000个多维表格"), "数量超过上限，不能接管");
    }

    @Test
    void topNSelectionSortsInJavaInsteadOfTrustingCliOrder() throws Exception {
        Class<?> fileType = Class.forName(
                "com.sunzeqin.feishuadmin.service.cli.SkillCliExecutorService$DriveFile");
        Class<?> intentType = Class.forName(
                "com.sunzeqin.feishuadmin.service.cli.SkillCliExecutorService$TopNFileIntent");

        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod("selectTopNFiles", List.class, intentType);
        method.setAccessible(true);

        // 故意打乱顺序：CLI 的 --sort create_time 是降序，Java 不能依赖它。
        List<Object> files = List.of(
                driveFile(fileType, "新", "tok-new", 3000L),
                driveFile(fileType, "老", "tok-old", 1000L),
                driveFile(fileType, "中", "tok-mid", 2000L));

        @SuppressWarnings("unchecked")
        List<Object> oldest = (List<Object>) method.invoke(service, files,
                intent(intentType, true, true, 2, false));
        assertEquals(List.of("老", "中"), titles(oldest), "「最早2个」必须自己排序后取最早的两个");

        @SuppressWarnings("unchecked")
        List<Object> newest = (List<Object>) method.invoke(service, files,
                intent(intentType, true, false, 2, false));
        assertEquals(List.of("新", "中"), titles(newest), "「最新2个」必须取最新的两个");
    }

    @Test
    void createTimeParsingRejectsGarbageSoDeletionStaysSafe() throws Exception {
        SkillCliExecutorService service = service();
        Method method = SkillCliExecutorService.class.getDeclaredMethod(
                "parseCreateTime", String.class, String.class);
        method.setAccessible(true);

        assertTrue((Long) method.invoke(service, "", "2023-01-02T03:04:05+08:00") > 0L,
                "ISO 字符串要能解析");
        assertEquals(1672600000000L, (Long) method.invoke(service, "1672600000", ""),
                "10 位 unix 时间戳按秒解析");
        assertEquals(1672600000000L, (Long) method.invoke(service, "1672600000000", ""),
                "13 位 unix 时间戳按毫秒解析");
        assertTrue((Long) method.invoke(service, "2023-01-02 03:04:05", "") > 0L,
                "带空格的日期时间也要能解析，CLI 有时给这种格式");
        assertTrue((Long) method.invoke(service, "", "2023-01-02") > 0L,
                "只到日期也要能解析");
        assertEquals(0L, (Long) method.invoke(service, "", ""),
                "取不出来必须是 0：调用方据此拒绝按不可信的排序做删除");
    }

    private Object field(Object target, String name) throws Exception {
        return target.getClass().getDeclaredMethod(name).invoke(target);
    }

    private Object driveFile(Class<?> type, String title, String token, long createTime) throws Exception {
        Constructor<?> ctor = type.getDeclaredConstructor(String.class, String.class, long.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(title, token, createTime, "");
    }

    private Object intent(Class<?> type, boolean deleteIntent, boolean oldest, int count, boolean createdByMe)
            throws Exception {
        Constructor<?> ctor = type.getDeclaredConstructor(boolean.class, boolean.class, int.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(deleteIntent, oldest, count, createdByMe);
    }

    private List<String> titles(List<Object> files) throws Exception {
        List<String> titles = new ArrayList<>();
        for (Object file : files) {
            titles.add((String) file.getClass().getMethod("title").invoke(file));
        }
        return titles;
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
