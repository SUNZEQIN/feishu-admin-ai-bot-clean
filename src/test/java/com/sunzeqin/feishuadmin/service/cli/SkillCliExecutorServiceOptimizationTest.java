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
