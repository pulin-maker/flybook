package com.bytedance.scheduler;

import com.bytedance.lock.DistributedLockService;
import com.bytedance.mq.WsBroadcastService;
import com.bytedance.websocket.AckPendingService;
import com.bytedance.websocket.PendingAckEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACK 超时重试定时任务
 * 每 10 秒扫描一次待确认队列，对未 ACK 的消息进行重推
 * 最多重试 3 次，超过后放弃（消息留在 DB 供离线同步）
 * 多实例部署时通过分布式锁保证只有一个实例执行
 */
@Component
@Slf4j
public class AckRetryScheduler {

    private final AckPendingService ackPendingService;
    private final WsBroadcastService wsBroadcastService;
    private final DistributedLockService distributedLockService;

    public AckRetryScheduler(AckPendingService ackPendingService,
                              WsBroadcastService wsBroadcastService,
                              DistributedLockService distributedLockService) {
        this.ackPendingService = ackPendingService;
        this.wsBroadcastService = wsBroadcastService;
        this.distributedLockService = distributedLockService;
    }

    @Scheduled(fixedDelay = 10000)
    public void retryPendingAcks() {
        boolean executed = distributedLockService.tryExecuteWithLock(
                "lock:scheduler:ack-retry", 30, () -> {
                    doRetry();
                });
        if (!executed) {
            log.debug("ACK 重试: 另一个实例正在执行，跳过本轮");
        }
    }

    private void doRetry() {
        List<PendingAckEntry> expired = ackPendingService.getExpiredPendingAcks();
        if (expired.isEmpty()) return;

        log.debug("ACK 重试: 发现 {} 条待重试消息", expired.size());

        for (PendingAckEntry entry : expired) {
            if (entry.getRetryCount() >= ackPendingService.getMaxRetryCount()) {
                ackPendingService.giveUp(entry.getMessageId(), entry.getUserId());
                log.info("ACK 重试耗尽: messageId={}, userId={}, 已重试{}次",
                        entry.getMessageId(), entry.getUserId(), entry.getRetryCount());
                continue;
            }

            // 通过广播重推（用户可能在任意实例上）
            wsBroadcastService.broadcast(entry.getUserId(), entry.getPushJson(), entry.getMessageId(), false);
            ackPendingService.incrementRetryCount(entry.getMessageId(), entry.getUserId());
            log.debug("ACK 广播重推: messageId={}, userId={}, retry={}",
                    entry.getMessageId(), entry.getUserId(), entry.getRetryCount() + 1);
        }
    }
}
