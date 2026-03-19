package com.bytedance;

import com.bytedance.modules.message.Message;
import com.bytedance.modules.conversation.IConversationService;
import com.bytedance.modules.message.IMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SendMessageFlow {

    // TODO: 测试环境下存在ServerContainer问题，请以ApiFox测试为准

    @Autowired
    private IMessageService iMessageService;

    @Autowired
    private IConversationService iConversationService; // 注入刚才写的 Service

    @Test
    void mainTest() {
        Long senderId = 1001L;

        // 1. 先创建一个会话
        System.out.println("正在初始化会话...");
        long conversationId = iConversationService.createConversation("测试群聊", 2, senderId);
        System.out.println("会话创建成功，ID: " + conversationId);

        // 2. 模拟发送第一条消息
        System.out.println("开始发送第一条消息...");
        Message msg1 = iMessageService.sendTextMsg(conversationId, senderId, "你好，这是第一条");
        System.out.println("消息1发送成功, Seq: " + msg1.getSeq());

        // 3. 模拟发送第二条消息
        System.out.println("开始发送第二条消息...");
        Message msg2 = iMessageService.sendTextMsg(conversationId, senderId, "这是第二条，Seq应该是连着的");
        System.out.println("消息2发送成功, Seq: " + msg2.getSeq());

        // 4. 简单验证
        // 如果是新创建的会话，Seq 应该是 1 和 2
        if (msg1.getSeq() == 1 && msg2.getSeq() == 2) {
            System.out.println(">>> 测试通过！Seq 逻辑正常 <<<");
        } else {
            System.err.println(">>> 测试失败：Seq 顺序不对 <<<");
        }
    }

}
