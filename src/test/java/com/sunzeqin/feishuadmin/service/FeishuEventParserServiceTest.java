package com.sunzeqin.feishuadmin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeishuEventParserServiceTest {

    @Test void parsesMessageEvent() {
        var parser = new FeishuEventParserService(new JsonUtils(new ObjectMapper()));
        var root = parser.parse("""
                {
                  "header":{"app_id":"cli_app","event_id":"evt","event_type":"im.message.receive_v1"},
                  "event":{
                    "sender":{"sender_id":{"open_id":"ou_sender","user_id":"u_sender"},"sender_type":"user"},
                    "message":{
                      "message_id":"om_1",
                      "chat_id":"oc_1",
                      "chat_type":"group",
                      "message_type":"text",
                      "content":"{\\"text\\":\\"hello @_user_1\\"}",
                      "mentions":[{"key":"@_user_1","name":"测试账号","id":{"open_id":"ou_1","user_id":"u_1","union_id":"on_1"}}]
                    }
                  }
                }
                """);

        var event = parser.messageEvent(root);
        assertNotNull(event);

        assertEquals("cli_app", event.appId());
        assertEquals("oc_1", event.chatId());
        assertEquals("hello @_user_1", event.text());
        assertEquals("测试账号", event.mentions().get(0).name());
    }
}
