package com.sunzeqin.feishuadmin.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextIntentUtilsTest {

    @Test void matchesCreateChatFromCurrentMembersIntent() {
        assertTrue(TextIntentUtils.createChatFromCurrentMembers(
                "新建一个群聊,名字为机器人测试群,把群里的机器人和用户都拉进去", "group"));
        assertEquals("机器人测试群", TextIntentUtils.chatName(
                "新建一个群聊,名字为机器人测试群,把群里的机器人和用户都拉进去", "默认群"));
        assertTrue(TextIntentUtils.includeUsers("只拉用户"));
        assertFalse(TextIntentUtils.includeBots("只拉用户"));
        assertFalse(TextIntentUtils.includeUsers("只拉机器人"));
        assertTrue(TextIntentUtils.includeBots("只拉机器人"));
        assertTrue(TextIntentUtils.targetNames("把群里的机器人和用户都拉进去").isEmpty());
        assertEquals(List.of("测试账号", "飞书智能员工"), TextIntentUtils.targetNames("把测试账号和飞书智能员工拉进去"));
    }
}
