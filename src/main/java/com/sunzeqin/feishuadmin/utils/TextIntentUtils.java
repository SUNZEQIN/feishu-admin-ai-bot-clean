package com.sunzeqin.feishuadmin.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本意图工具类。
 *
 * <p>作用：处理当前 demo 里的轻量中文意图判断和参数提取。复杂场景后续再交给 LLM。</p>
 *
 * @author sunzeqin
 */
public final class TextIntentUtils {
    // 用正则从用户文本里提取群名，例如“名字为机器人测试群”。
    private static final Pattern CHAT_NAME_PATTERN = Pattern.compile("(?:名字为|名称为|群名为|叫做|叫)\\s*([^,，。\\s]+)");

    // 用正则从用户文本里提取“把 A 和 B 拉进去”里的 A、B。
    private static final Pattern TARGET_NAMES_PATTERN = Pattern.compile("(?:把|拉|邀请|加入)\\s*([^,，。\\s]+(?:和[^,，。\\s]+)*)\\s*(?:拉进去|拉入|加入|邀请|进群)");

    private TextIntentUtils() {
        // 工具类不需要创建对象，所以构造方法设为 private。
    }

    /**
     * 判断是否为“从当前群成员创建新群”的请求。
     *
     * @param text     用户文本
     * @param chatType 当前会话类型
     * @return 是否命中
     */
    public static boolean createChatFromCurrentMembers(String text, String chatType) {
        // 去掉空格和换行，降低用户输入格式对意图判断的影响。
        String normalized = normalize(text);

        // 当前功能只允许在群聊里触发，因为要读取“当前群”的成员。
        return "group".equals(chatType)
                // 用户文本里需要出现“新建”或“创建”。
                && (normalized.contains("新建") || normalized.contains("创建"))
                // 用户文本里需要明确提到“群”。
                && normalized.contains("群")
                // 用户文本里需要说明成员来源是当前群。
                && (normalized.contains("群里的") || normalized.contains("当前群") || normalized.contains("这个群"))
                // 用户文本里需要说明要把成员拉入新群。
                && (normalized.contains("拉进去") || normalized.contains("拉入") || normalized.contains("加入"));
    }

    /**
     * 从用户文本中提取群名。
     *
     * @param text        用户文本
     * @param defaultName 默认群名
     * @return 群名
     */
    public static String chatName(String text, String defaultName) {
        // 用正则匹配用户输入里的群名称。
        var matcher = CHAT_NAME_PATTERN.matcher(text == null ? "" : text);

        // 如果匹配到了群名，就返回用户指定的群名。
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 如果没有匹配到群名，就返回默认群名。
        return defaultName;
    }

    public static boolean includeUsers(String text) {
        // 先把文本规整成无空白字符串。
        String normalized = normalize(text);

        // 用户明确说“只拉机器人”时，不拉普通用户。
        if (normalized.contains("只拉机器人") || normalized.contains("仅拉机器人") || normalized.contains("只邀请机器人")) {
            return false;
        }

        // 用户明确说“不要用户”时，不拉普通用户。
        if (normalized.contains("不要用户") || normalized.contains("不拉用户")) {
            return false;
        }

        // 默认允许拉普通用户，因为建群通常至少需要人参与。
        return true;
    }

    public static boolean includeBots(String text) {
        // 先把文本规整成无空白字符串。
        String normalized = normalize(text);

        // 用户明确说“不要机器人”时，不拉机器人。
        if (normalized.contains("不要机器人") || normalized.contains("不拉机器人")) {
            return false;
        }

        // 用户明确说“只拉用户”时，不拉机器人。
        if (normalized.contains("只拉用户") || normalized.contains("仅拉用户") || normalized.contains("只邀请用户")) {
            return false;
        }

        // 文本里提到机器人时，说明需要拉机器人。
        if (normalized.contains("机器人")) {
            return true;
        }

        // 文本里提到“成员”“所有人”“全部”时，通常表示当前群全量迁移，包含机器人。
        if (normalized.contains("成员") || normalized.contains("所有人") || normalized.contains("全部")) {
            return true;
        }

        // 默认不拉机器人，避免用户只说“拉张三”时误把机器人也拉进去。
        return false;
    }

    public static List<String> targetNames(String text) {
        // 保存从文本里提取到的目标名称。
        List<String> names = new ArrayList<>();

        // 用正则查找类似“把张三和测试机器人拉进去”的片段。
        var matcher = TARGET_NAMES_PATTERN.matcher(text == null ? "" : text);

        // 没匹配到目标名称时，返回空列表，表示后续按类型全量拉入。
        if (!matcher.find()) {
            return names;
        }

        // 取出名称片段，例如“张三和测试机器人”。
        String rawNames = matcher.group(1);

        // 如果用户说的是“当前群/群里的/这个群”，不是指定人名，返回空列表表示全量选择。
        if (rawNames.contains("当前群") || rawNames.contains("群里的") || rawNames.contains("这个群")) {
            return names;
        }

        // 按中文连接词和常见标点拆分多个名称。
        String[] parts = rawNames.split("和|、|,|，");

        // 遍历拆分结果。
        for (String part : parts) {
            // 去掉名称两边空格。
            String name = part.trim();

            // 空名称不加入列表。
            if (!name.isBlank()) {
                names.add(name);
            }
        }

        // 返回目标名称列表。
        return names;
    }

    private static String normalize(String value) {
        // 空文本按空字符串处理；非空文本去掉所有空白字符。
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
