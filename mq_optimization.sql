-- MQ 企业级优化 SQL 脚本（RabbitMQ 版本）

-- 1. 本地消息表（Outbox Pattern）
CREATE TABLE mq_outbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id   BIGINT       NOT NULL COMMENT '关联的 messages.message_id',
    exchange     VARCHAR(128) NOT NULL COMMENT 'RabbitMQ Exchange',
    routing_key  VARCHAR(128) DEFAULT '' COMMENT 'RabbitMQ Routing Key',
    body         TEXT         NOT NULL COMMENT '消息体 JSON',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=PENDING, 1=SENT, 2=FAILED',
    retry_count  INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_create (status, create_time),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 本地消息表';

-- 2. 失败消息表（死信队列持久化）
CREATE TABLE mq_failed_messages (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id   BIGINT       NULL COMMENT '关联的 messages.message_id',
    exchange     VARCHAR(128) NOT NULL COMMENT 'RabbitMQ Exchange',
    body         TEXT         NOT NULL COMMENT '消息体 JSON',
    fail_reason  VARCHAR(512) DEFAULT '' COMMENT '失败原因',
    resolved     TINYINT      DEFAULT 0 COMMENT '0=未处理, 1=已处理',
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_resolved (resolved),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 失败消息表';
