package com.sunzeqin.feishuadmin.config;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置默认值一致性测试（产品规则 G-06）。
 *
 * <p>作用：把「代码默认值必须与 application.yml 一致」变成一条自动化断言，
 * 避免换环境时行为漂移 —— 这类漂移在单测里看不见，上线才发现，代价很高。</p>
 *
 * <p>做法：解析 application.yml 里 `feishu:` 段的 `${ENV:默认值}` 占位符，
 * 反射调用 `FeishuProperties` 对应 getter，逐个比对默认值。</p>
 *
 * @author sunzeqin
 */
class FeishuPropertiesDefaultsTest {

    // 匹配 ${ENV_NAME:默认值}，默认值可以为空。
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+):([^}]*)}");

    // 至少要真的比对这么多项，否则测试本身失去意义（防止解析失败后空跑通过）。
    private static final int MIN_COMPARED = 15;

    @Test
    void propertyDefaultsMatchApplicationYml() throws Exception {
        // 收集不一致项。
        List<String> mismatches = new ArrayList<>();

        // 记录实际比对项数。
        int compared = 0;

        // 逐行解析 feishu 段。
        for (String[] entry : feishuYmlPlaceholders()) {
            // entry[0] = yml 键（kebab-case），entry[1] = 默认值。
            String key = entry[0];
            String expected = entry[1];

            // 找到对应的 getter。
            Method getter = findGetter(key);

            // 找不到 getter 说明这个配置项没有对应 Java 字段，跳过。
            if (getter == null) {
                continue;
            }

            // 读取代码默认值。
            Object actual = getter.invoke(new FeishuProperties());

            // null 统一按空字符串处理，避免 getter 返回 null 造成假失败。
            String actualText = actual == null ? "" : actual.toString();

            // 比对。
            compared++;
            if (!expected.equals(actualText)) {
                mismatches.add(key + " → application.yml=" + expected + "，代码默认值=" + actualText);
            }
        }

        // 断言真的比对到了足够多的项。
        assertTrue(compared >= MIN_COMPARED, "只比对了 " + compared + " 项，配置解析可能失效");

        // 断言没有不一致。
        assertTrue(mismatches.isEmpty(), "代码默认值与 application.yml 不一致：\n" + String.join("\n", mismatches));
    }

    private List<String[]> feishuYmlPlaceholders() throws Exception {
        // 从 classpath 读取 application.yml。
        InputStream stream = FeishuPropertiesDefaultsTest.class.getClassLoader().getResourceAsStream("application.yml");
        assertTrue(stream != null, "找不到 application.yml");

        List<String[]> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            boolean inFeishuBlock = false;
            String line;
            while ((line = reader.readLine()) != null) {
                // 空行跳过。
                if (line.isBlank()) {
                    continue;
                }

                // 顶层键开始：判断是否进入 feishu 段。
                if (!line.startsWith(" ")) {
                    inFeishuBlock = line.startsWith("feishu:");
                    continue;
                }

                // 不在 feishu 段里就跳过。
                if (!inFeishuBlock) {
                    continue;
                }

                // 只处理两个空格缩进的配置项。
                if (!line.startsWith("  ") || line.startsWith("   ")) {
                    continue;
                }

                // 拆出 key 与 value。
                int splitAt = line.indexOf(':', 2);
                if (splitAt < 0) {
                    continue;
                }
                String key = line.substring(2, splitAt).trim();
                String value = line.substring(splitAt + 1).trim();

                // 只处理带默认值的占位符。
                Matcher matcher = PLACEHOLDER.matcher(value);
                if (matcher.find()) {
                    result.add(new String[]{key, matcher.group(2)});
                }
            }
        }

        // 返回解析结果。
        return result;
    }

    private Method findGetter(String kebabKey) {
        // kebab-case 转成 CamelCase。
        StringBuilder camel = new StringBuilder();
        for (String part : kebabKey.split("-")) {
            if (part.isEmpty()) {
                continue;
            }
            camel.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        // 先找 getXxx，再找 isXxx（布尔配置）。
        for (String prefix : new String[]{"get", "is"}) {
            try {
                return FeishuProperties.class.getMethod(prefix + camel);
            } catch (NoSuchMethodException ignored) {
                // 继续尝试下一个前缀。
            }
        }

        // 没有对应 getter。
        return null;
    }
}
