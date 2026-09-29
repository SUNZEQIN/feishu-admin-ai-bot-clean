package com.sunzeqin.feishuadmin.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * JSON 工具类。
 *
 * <p>作用：统一封装 Jackson 的读取和序列化，避免业务代码到处 try/catch。</p>
 *
 * @author sunzeqin
 */
@Component
public class JsonUtils {
    // Jackson 的核心对象，用来做 JSON 字符串和 Java 对象之间的转换。
    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        // 保存 Spring 注入进来的 ObjectMapper，避免每次使用都 new 一个。
        this.objectMapper = objectMapper;
    }

    /**
     * 把 JSON 字符串解析为 JsonNode。
     *
     * @param json JSON 字符串
     * @return JsonNode
     */
    public JsonNode readTree(String json) {
        try {
            // 把 JSON 字符串解析成树形结构，方便后面用 path 读取字段。
            return objectMapper.readTree(json);
        } catch (Exception e) {
            // 解析失败时统一抛业务可读的异常，外层不用关心 Jackson 的细节。
            throw new IllegalArgumentException("JSON 解析失败", e);
        }
    }

    /**
     * 把对象序列化为 JSON 字符串。
     *
     * @param value 任意对象
     * @return JSON 字符串
     */
    public String write(Object value) {
        try {
            // 把 Java 对象序列化成 JSON 字符串，常用于请求飞书接口。
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 序列化失败时统一抛业务可读的异常。
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }

    public Map<String, Object> convertToMap(JsonNode node) {
        try {
            // 把 JsonNode 转成 Map，主要用于解析 LLM 输出的 tool.params。
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            // 转换失败时统一抛业务可读异常。
            throw new IllegalArgumentException("JSON 转 Map 失败", e);
        }
    }
}
