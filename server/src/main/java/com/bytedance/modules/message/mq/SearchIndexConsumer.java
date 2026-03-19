package com.bytedance.modules.message.mq;
import com.bytedance.infrastructure.mq.RoutingKeys;
import com.bytedance.infrastructure.mq.QueueNames;

import cn.hutool.json.JSONUtil;
import com.bytedance.modules.search.MessageDocument;
import com.bytedance.modules.message.event.MessageEditedEvent;
import com.bytedance.modules.message.event.MessageRevokedEvent;
import com.bytedance.modules.message.event.MessageSentEvent;
import com.bytedance.modules.search.MessageSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 搜索索引消费者
 * 监听 msg.sent / msg.edited / msg.revoked 事件，同步到 Elasticsearch
 */
@Component
@Slf4j
public class SearchIndexConsumer {

    private static final String CONSUMED_KEY_PREFIX = "mq:search-consumed:";

    private final StringRedisTemplate stringRedisTemplate;
    private final MessageSearchService messageSearchService;

    public SearchIndexConsumer(StringRedisTemplate stringRedisTemplate,
                               MessageSearchService messageSearchService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageSearchService = messageSearchService;
    }

    @RabbitListener(queues = QueueNames.SEARCH, containerFactory = "retryContainerFactory")
    public void onMessage(Message message) {
        String eventJson = new String(message.getBody());
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        log.debug("SearchIndexConsumer received: routingKey={}", routingKey);

        try {
            switch (routingKey) {
                case RoutingKeys.MSG_SENT:
                    handleSent(eventJson);
                    break;
                case RoutingKeys.MSG_EDITED:
                    handleEdited(eventJson);
                    break;
                case RoutingKeys.MSG_REVOKED:
                    handleRevoked(eventJson);
                    break;
                default:
                    log.warn("SearchIndexConsumer unknown routingKey: {}", routingKey);
            }
        } catch (Exception e) {
            log.error("SearchIndexConsumer failed: routingKey={}, body={}", routingKey, eventJson, e);
            throw e;
        }
    }

    private void handleSent(String eventJson) {
        MessageSentEvent event = JSONUtil.toBean(eventJson, MessageSentEvent.class);

        // 幂等检查
        String consumedKey = CONSUMED_KEY_PREFIX + "sent:" + event.getMessageId();
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(consumedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            log.debug("SearchIndexConsumer duplicate sent skipped: messageId={}", event.getMessageId());
            return;
        }

        String textContent = extractTextContent(event.getContent(), event.getMsgType());

        MessageDocument doc = MessageDocument.builder()
                .messageId(event.getMessageId())
                .conversationId(event.getConversationId())
                .senderId(event.getSenderId())
                .seq(event.getSeq())
                .msgType(event.getMsgType())
                .content(textContent)
                .mentions(event.getMentions())
                .quoteId(event.getQuoteId())
                .isRevoked(false)
                .isEdited(false)
                .createdTime(parseTime(event.getCreatedTime()))
                .build();

        messageSearchService.indexMessage(doc);
        log.debug("SearchIndexConsumer indexed: messageId={}", event.getMessageId());
    }

    private void handleEdited(String eventJson) {
        MessageEditedEvent event = JSONUtil.toBean(eventJson, MessageEditedEvent.class);
        String textContent = extractTextContent(event.getNewContent(), 1);
        messageSearchService.updateMessage(event.getMessageId(), textContent);
        log.debug("SearchIndexConsumer updated (edit): messageId={}", event.getMessageId());
    }

    private void handleRevoked(String eventJson) {
        MessageRevokedEvent event = JSONUtil.toBean(eventJson, MessageRevokedEvent.class);
        messageSearchService.revokeMessage(event.getMessageId());
        log.debug("SearchIndexConsumer updated (revoke): messageId={}", event.getMessageId());
    }

    /**
     * 从 content JSON 中提取可搜索的文本
     */
    private String extractTextContent(String contentJson, Integer msgType) {
        if (msgType == null || contentJson == null) return "";
        try {
            if (msgType == 1) {
                return JSONUtil.parseObj(contentJson).getStr("text", "");
            } else if (msgType == 2) {
                return "[图片]";
            } else if (msgType == 5) {
                String title = JSONUtil.parseObj(contentJson).getStr("title", "");
                return "[待办] " + title;
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from content: {}", contentJson, e);
        }
        return contentJson;
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(timeStr);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
