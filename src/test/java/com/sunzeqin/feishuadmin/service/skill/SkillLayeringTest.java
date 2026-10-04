package com.sunzeqin.feishuadmin.service.skill;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill 分层自检测试。
 *
 * <p>作用：把「两层 Skill」的约定固化成测试，防止后续改动时层级漂移。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>第一层 {@code skills/lark/} 只描述 lark-cli 在该业务域的能力，不含业务规则；</li>
 *   <li>第二层 {@code skills/business/} 只描述业务规则和工具链路由，不含 CLI 命令索引；</li>
 *   <li>配置里允许的每个业务域，都必须有对应的第一层 Skill 文件。</li>
 * </ul>
 *
 * @author sunzeqin
 */
class SkillLayeringTest {

    // 第一层 Skill 目录。
    private static final String LARK_DIR = "skills/lark/";

    // 第二层 Skill 目录。
    private static final String BUSINESS_DIR = "skills/business/";

    // 业务规则关键词：出现在第一层说明层级混了。
    private static final List<String> BUSINESS_KEYWORDS = List.of(
            "电商", "订单", "库存", "退款", "GMV", "客户画像", "MCP");

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    void everyAllowedDomainHasLarkSkill() throws Exception {
        // 配置里允许的业务域，必须每个都有第一层 Skill，否则模型拿不到能力说明。
        Set<String> domains = new LinkedHashSet<>();
        for (String item : new FeishuProperties().getCliAllowedDomains().split(",")) {
            String domain = item.trim().toLowerCase();
            if (!domain.isBlank()) {
                domains.add(domain);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String domain : domains) {
            if (!resolver.getResource("classpath:" + LARK_DIR + domain + ".md").exists()) {
                missing.add(domain);
            }
        }

        // FeishuProperties 的 Java 默认值覆盖 8 个核心域；application.yml 会把它扩到 15 个。
        // 这里校验的是「配置里写了的域，必须都有第一层 Skill」，不锁死具体数量。
        assertTrue(domains.size() >= 8, "允许的业务域数量异常，实际=" + domains.size());
        assertTrue(missing.isEmpty(), "以下业务域缺少第一层 Skill：" + missing);
    }

    @Test
    void larkSkillsDoNotContainBusinessRules() throws Exception {
        // 第一层只讲飞书能力，出现业务关键词说明层级混了。
        List<String> offenders = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:" + LARK_DIR + "*.md")) {
            String text = resource.getContentAsString(StandardCharsets.UTF_8);
            for (String keyword : BUSINESS_KEYWORDS) {
                if (text.contains(keyword)) {
                    offenders.add(resource.getFilename() + " 含业务关键词=" + keyword);
                }
            }
        }

        assertTrue(offenders.isEmpty(), "第一层 Skill 混入业务规则：" + offenders);
    }

    @Test
    void businessSkillsDoNotContainCliCommandIndex() throws Exception {
        // 第二层只讲业务规则和工具链路由，出现 lark-cli 命令索引说明层级混了。
        List<String> offenders = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:" + BUSINESS_DIR + "*.md")) {
            String text = resource.getContentAsString(StandardCharsets.UTF_8);
            for (String line : Arrays.asList(text.split("\n"))) {
                // 只拦截真正的命令索引行，例如 "lark-cli base +table-list --help"。
                if (line.trim().startsWith("lark-cli ")) {
                    offenders.add(resource.getFilename() + " 含 CLI 命令索引行=" + line.trim());
                }
            }
        }

        assertTrue(offenders.isEmpty(), "第二层 Skill 混入 CLI 命令索引：" + offenders);
    }

    @Test
    void skillLayersArePhysicallySeparated() throws Exception {
        // 两层目录必须真实存在且有内容，避免“文档说分了层、代码没分”。
        assertTrue(resolver.getResources("classpath*:" + LARK_DIR + "*.md").length >= 15,
                "第一层 Skill 文件数量不足");
        assertTrue(resolver.getResources("classpath*:" + BUSINESS_DIR + "*.md").length >= 1,
                "第二层 Skill 文件缺失");

        // 旧的扁平目录若还存在，说明迁移没做干净。
        assertFalse(resolver.getResource("classpath:skills/ecommerce-agent.md").exists(),
                "旧的扁平路径 skills/ecommerce-agent.md 仍存在，迁移不完整");
    }
}
