package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 电商结果脱敏服务。
 *
 * <p>作用：把「谁能看到哪些字段」这件事放在 Java 层强制完成，模型只负责组织语言。</p>
 *
 * <p>为什么必须在这里做：模型的提示词是可以被绕过的，
 * 只要原始数据进了上下文，就有被复述出来的可能。所以脱敏要在结果进入模型之前完成。</p>
 *
 * <p>规则来源：产品需求 4.2 脱敏规则 + 产品规则 P-04。</p>
 *
 * @author sunzeqin
 */
@Service
public class ResultMaskingService {

    // 当前服务使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(ResultMaskingService.class);

    // 任何角色都不能看到的字段：密钥类信息，属于永久红线。
    private static final Set<String> SECRET_STEMS = Set.of(
            "secret", "token", "password", "passwd", "apikey", "privatekey", "credential");

    // 个人隐私字段：L1、L2 都看不到（客户姓名可以给 L2，但手机号地址不行）。
    private static final Set<String> PERSONAL_STEMS = Set.of(
            "phone", "mobile", "telephone", "address", "addr", "idcard", "idno",
            "paymentno", "transactionno", "bankcard", "cardno", "receiver");

    // 只有 L1 看不到的字段：客户维度与成本利润维度。
    private static final Set<String> L1_ONLY_HIDDEN_STEMS = Set.of(
            "customer", "cost", "margin", "profit", "purchaseprice");

    // 手机号形态的兜底识别：即使字段名没暴露，值里也不许出现。
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    // 脱敏占位符。
    private static final String MASKED_VALUE = "***";

    /**
     * 按角色裁剪电商工具返回的数据。
     *
     * @param role   调用者角色，null 按 L1 处理
     * @param call   本次工具调用
     * @param result 工具原始结果
     * @return 裁剪后的结果；非电商结果原样返回
     */
    public ToolResult mask(BotRole role, ToolCall call, ToolResult result) {
        // 空结果无法裁剪。
        if (result == null || result.data().isEmpty()) {
            return result;
        }

        // 只有电商网关返回的业务数据需要裁剪。
        if (call == null || !"ecommerce.call_tool".equals(call.name())) {
            return result;
        }

        // 角色为空按最小权限处理。
        BotRole effective = role == null ? BotRole.defaultRole() : role;

        // 递归裁剪。
        Map<String, Object> masked = maskMap(effective, result.data());

        // 记录一条脱敏日志，方便验证"脱敏真的发生了"。
        log.info("[阶段5 权限分级] 电商结果脱敏完成：角色={}，工具={}，原始字段数={}，脱敏后字段数={}",
                effective, call.params().get("toolName"), result.data().size(), masked.size());

        // 返回裁剪后的结果，错误码等其它字段保持不变。
        return new ToolResult(result.success(), result.tool(), result.message(), masked, result.errorCode());
    }

    private Map<String, Object> maskMap(BotRole role, Map<String, Object> source) {
        // 新建 Map，不修改原始数据，避免影响审计与后续步骤。
        Map<String, Object> target = new HashMap<>();

        // 逐字段判断。
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            // 字段名空值跳过。
            if (entry.getKey() == null) {
                continue;
            }

            // 命中黑名单的字段直接丢弃。
            if (hidden(role, entry.getKey())) {
                continue;
            }

            // 保留字段，值做递归处理。
            target.put(entry.getKey(), maskValue(role, entry.getValue()));
        }

        // 返回裁剪结果。
        return target;
    }

    private Object maskValue(BotRole role, Object value) {
        // 嵌套 Map 递归处理。
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = entry.getKey().toString();
                if (hidden(role, key)) {
                    continue;
                }
                nested.put(key, maskValue(role, entry.getValue()));
            }
            return nested;
        }

        // 列表逐项处理。
        if (value instanceof List<?> list) {
            List<Object> items = new ArrayList<>(list.size());
            for (Object item : list) {
                items.add(maskValue(role, item));
            }
            return items;
        }

        // 字符串里的手机号兜底打码（L3 管理员不限制）。
        if (value instanceof String text && !role.atLeast(BotRole.L3)) {
            return PHONE_PATTERN.matcher(text).replaceAll(MASKED_VALUE);
        }

        // 其它类型原样返回。
        return value;
    }

    /**
     * 判断某个字段在当前角色下是否必须隐藏。
     *
     * @param role 角色
     * @param key  字段名
     * @return true 表示隐藏
     */
    static boolean hidden(BotRole role, String key) {
        // 字段名规范化：统一小写并去掉下划线与横线。
        String normalized = normalize(key);

        // 密钥类字段：所有角色都看不到。
        if (containsAny(normalized, SECRET_STEMS)) {
            return true;
        }

        // 个人隐私字段：L1、L2 看不到。
        if (!role.atLeast(BotRole.L3) && containsAny(normalized, PERSONAL_STEMS)) {
            return true;
        }

        // 客户维度与成本利润字段：只有 L1 看不到。
        if (!role.atLeast(BotRole.L2) && containsAny(normalized, L1_ONLY_HIDDEN_STEMS)) {
            return true;
        }

        // 其余字段保留。
        return false;
    }

    private static boolean containsAny(String normalizedKey, Set<String> stems) {
        // 任一命中即认为敏感。
        for (String stem : stems) {
            if (normalizedKey.contains(stem)) {
                return true;
            }
        }

        // 没命中。
        return false;
    }

    private static String normalize(String key) {
        // 空值归一成空字符串。
        if (key == null) {
            return "";
        }

        // 去掉下划线和横线后再比对，兼容 cost_price / costPrice / cost-price。
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
