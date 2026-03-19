package com.bytedance.infrastructure.scheduler;

import com.bytedance.infrastructure.mq.MqOutbox;
import com.bytedance.infrastructure.concurrent.DistributedLockService;
import com.bytedance.infrastructure.mq.MqOutboxMapper;
import com.bytedance.modules.message.mq.MessageEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 补偿重试定时任务
 * 每 10 秒扫描一次 PENDING 状态的 Outbox 记录，尝试重新发送到 MQ
 * 最多重试 5 次，超过后标记为 FAILED
 * 多实例部署时通过分布式锁保证只有一个实例执行
 */
@Component
@Slf4j
public class OutboxRetryScheduler {

    private final MqOutboxMapper mqOutboxMapper;
    private final MessageEventProducer messageEventProducer;
    private final DistributedLockService distributedLockService;

    @Value("${flybook.mq.outbox.max-retry:5}")
    private int maxRetry;

    @Value("${flybook.mq.outbox.batch-size:100}")
    private int batchSize;

    public OutboxRetryScheduler(MqOutboxMapper mqOutboxMapper,
                                 MessageEventProducer messageEventProducer,
                                 DistributedLockService distributedLockService) {
        this.mqOutboxMapper = mqOutboxMapper;
        this.messageEventProducer = messageEventProducer;
        this.distributedLockService = distributedLockService;
    }

    @Scheduled(fixedDelayString = "${flybook.mq.outbox.retry-interval-ms:10000}")
    public void retryPendingOutbox() {
        boolean executed = distributedLockService.tryExecuteWithLock(
                "lock:scheduler:outbox-retry", 30, () -> {
                    doRetry();
                });
        if (!executed) {
            log.debug("Outbox 补偿: 另一个实例正在执行，跳过本轮");
        }
    }

    private void doRetry() {
        List<MqOutbox> pendingRecords = mqOutboxMapper.findPendingRecords(maxRetry, batchSize);
        if (pendingRecords.isEmpty()) return;

        log.debug("Outbox 补偿: 发现 {} 条待重试记录", pendingRecords.size());

        for (MqOutbox outbox : pendingRecords) {
            if (outbox.getRetryCount() >= maxRetry) {
                // 超过最大重试次数，标记为 FAILED
                mqOutboxMapper.casUpdateStatus(outbox.getId(), MqOutbox.Status.PENDING, MqOutbox.Status.FAILED);
                log.warn("Outbox 重试耗尽: outboxId={}, messageId={}, 已重试{}次",
                        outbox.getId(), outbox.getMessageId(), outbox.getRetryCount());
                continue;
            }

            // 尝试重新发送
            messageEventProducer.sendWithOutbox(outbox.getId(), outbox.getRoutingKey(), outbox.getBody());
        }
    }
}
