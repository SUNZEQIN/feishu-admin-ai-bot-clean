package com.sunzeqin.feishuadmin.service.agent;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据来源标注服务。
 *
 * <p>作用：V1 使用的是测试数据，回复必须标注来源，避免用户把测试数字当成真实经营数据。</p>
 *
 * <p>规则来源：产品需求第 4 节第 7 条 + 产品规则 C-08。</p>
 *
 * @author sunzeqin
 */
@Service
public class DataSourceNoticeService {

    // 标注文案（定稿）。
    public static final String NOTICE_TEXT = "数据来源：测试数据";

    // 电商业务工具名：只有它返回的才是电商数据。
    private static final String ECOMMERCE_TOOL = "ecommerce.call_tool";

    // 飞书配置，用来读取标注开关。
    private final FeishuProperties properties;

    public DataSourceNoticeService(FeishuProperties properties) {
        // 保存配置。
        this.properties = properties;
    }

    /**
     * 给回复追加数据来源标注。
     *
     * @param reply        模型生成的最终回复
     * @param observations 本次任务的所有工具观察结果
     * @return 追加标注后的回复；没用到电商数据时原样返回
     */
    public String apply(String reply, List<ToolResult> observations) {
        // 开关关闭时不动回复。
        if (!properties.isEcommerceDataSourceNoticeEnabled()) {
            return reply;
        }

        // 空回复没有可标注的内容。
        if (reply == null || reply.isBlank()) {
            return reply;
        }

        // 没有真的取到电商数据就不标注，避免给纯飞书操作贴电商标签。
        if (!usedEcommerceData(observations)) {
            return reply;
        }

        // 已经标注过就不重复追加。
        if (reply.contains(NOTICE_TEXT)) {
            return reply;
        }

        // 追加在回复底部。
        return reply.stripTrailing() + "\n\n" + NOTICE_TEXT;
    }

    private boolean usedEcommerceData(List<ToolResult> observations) {
        // 没有观察结果就没有数据。
        if (observations == null || observations.isEmpty()) {
            return false;
        }

        // 只要有任意一次电商调用成功，就认为回复里用到了电商数据。
        for (ToolResult result : observations) {
            if (result != null && result.success() && ECOMMERCE_TOOL.equals(result.tool())) {
                return true;
            }
        }

        // 没有成功的电商调用。
        return false;
    }
}
