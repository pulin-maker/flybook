package com.bytedance.scheduler;

import com.bytedance.websocket.AckPendingService;
import com.bytedance.websocket.PendingAckEntry;
import com.bytedance.websocket.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACK 超时重试定时任务
 * 每 10 秒扫描一次待确认队列，对未 ACK 的消息进行重推
 * 最多重试 3 次，超过后放弃（消息留在 DB 供离线同步）
 */
@Component
@Slf4j
public class AckRetryScheduler {

    private final AckPendingService ackPendingService;
    private final SessionRegistry sessionRegistry;

    public AckRetryScheduler(AckPendingService ackPendingService, SessionRegistry sessionRegistry) {
        this.ackPendingService = ackPendingService;
        this.sessionRegistry = sessionRegistry;
    }

    @Scheduled(fixedDelay = 10000)
    public void retryPendingAcks() {
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

            boolean delivered = sessionRegistry.pushMessage(entry.getUserId(), entry.getPushJson());
            if (delivered) {
                // 推送成功，增加重试次数，等待下一轮 ACK 确认
                ackPendingService.incrementRetryCount(entry.getMessageId(), entry.getUserId());
                log.debug("ACK 重推成功: messageId={}, userId={}, retry={}",
                        entry.getMessageId(), entry.getUserId(), entry.getRetryCount() + 1);
            } else {
                // 用户不在线，放弃重试
                ackPendingService.giveUp(entry.getMessageId(), entry.getUserId());
                log.debug("用户离线放弃重试: messageId={}, userId={}", entry.getMessageId(), entry.getUserId());
            }
        }
    }
}
