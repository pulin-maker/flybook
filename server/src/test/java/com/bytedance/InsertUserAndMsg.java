package com.bytedance;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bytedance.modules.message.Message;
import com.bytedance.modules.user.User;
import com.bytedance.modules.message.MessageMapper;
import com.bytedance.modules.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
class InsertUserAndMsg {

    // TODO: 测试环境下存在ServerContainer问题，请以ApiFox测试为准

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Test
    void testInsert() {
        // 1. 测试插入用户
        User user = User.builder()
                .username("HutoolUser")
                .avatarUrl("http://avatar.com/2.jpg")
                .createdTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        System.out.println("插入用户成功 ID: " + user.getUserId());

        // 2. 测试插入 JSON 消息
        // 使用 Hutool 构建 JSON 对象
        JSONObject contentJson = JSONUtil.createObj()
                .set("text", "Hello MyBatis Plus")
                .set("extra", "Some data");

        Message msg = Message.builder()
                .conversationId(100L)
                .senderId(user.getUserId())
                .seq(3L)
                .msgType(1)
                .content(contentJson.toString()) // 转成 String 存入
                .createdTime(LocalDateTime.now())
                .build();

        messageMapper.insert(msg);
        System.out.println("插入消息成功 ID: " + msg.getMessageId());
    }
}

