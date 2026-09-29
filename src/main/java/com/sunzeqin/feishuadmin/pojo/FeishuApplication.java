package com.sunzeqin.feishuadmin.pojo;

/**
 * 飞书企业安装应用。
 *
 * <p>作用：保存飞书应用列表接口返回的应用信息，用来把机器人名称匹配成创建群聊需要的 app_id。</p>
 *
 * @param appId   应用 ID，通常是 cli_ 开头，创建群聊时放到 bot_id_list
 * @param appName 应用名称，也就是用户在飞书里看到的机器人/应用名称
 *
 * @author sunzeqin
 */
public record FeishuApplication(String appId, String appName) {
}
