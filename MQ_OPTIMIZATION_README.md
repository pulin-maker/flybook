# MQ 企业级优化实施文档（RabbitMQ 版本）

## 概述

本次优化解决了 MQ 系统的四个核心问题：
1. ✅ MySQL 和 MQ 双写一致性（Outbox Pattern）
2. ✅ 消费端幂等保护（Redis SET NX）
3. ✅ 死信队列 + 失败消息持久化
4. ✅ 多消费者架构（RoutingKey 路由）

**注意**：2025-03 完成了 RocketMQ → RabbitMQ 迁移，所有 `topic/tag` 概念已改为 `exchange/routingKey`。

---

## 一、数据库变更

### 执行 SQL（迁移脚本）

```sql
-- 迁移现有数据
ALTER TABLE mq_outbox
  CHANGE COLUMN topic exchange VARCHAR(128) NOT NULL COMMENT 'RabbitMQ Exchange',
  CHANGE COLUMN tag routing_key VARCHAR(128) DEFAULT '' COMMENT 'RabbitMQ Routing Key';

UPDATE mq_outbox SET exchange = 'flybook.message' WHERE exchange = 'flybook-message-push';
UPDATE mq_outbox SET routing_key = 'msg.sent' WHERE routing_key = 'MSG_SENT';
UPDATE mq_outbox SET routing_key = 'msg.revoked' WHERE routing_key = 'MSG_REVOKED';
UPDATE mq_outbox SET routing_key = 'msg.edited' WHERE routing_key = 'MSG_EDITED';
UPDATE mq_outbox SET routing_key = 'reaction.changed' WHERE routing_key = 'REACTION_CHANGED';

ALTER TABLE mq_failed_messages
  CHANGE COLUMN topic exchange VARCHAR(128) NOT NULL COMMENT 'RabbitMQ Exchange';
```

### 新增表

1. **mq_outbox** - 本地消息表
   - 保证 MySQL 和 MQ 的双写一致性
   - 定时任务扫描 PENDING 记录补偿重发

2. **mq_failed_messages** - 失败消息表
   - 持久化死信队列消息
   - 供人工排查和处理

---

## 二、RabbitMQ 拓扑

### Exchange
| Exchange | 类型 | 说明 |
|----------|------|------|
| `flybook.message` | Topic | 主业务交换机 |
| `flybook.message.dlx` | Topic | 死信交换机 |

### Queue + Binding
| Queue | Routing Key | 消费者 |
|-------|-------------|--------|
| `flybook.push.queue` | `msg.sent` | MessagePushConsumer |
| `flybook.revoke.queue` | `msg.revoked` | MessageRevokeConsumer |
| `flybook.edit.queue` | `msg.edited` | MessageEditConsumer |
| `flybook.reaction.queue` | `reaction.changed` | ReactionPushConsumer |
| `flybook.search.queue` | `msg.sent` | SearchIndexConsumer |
| `flybook.unread.queue` | `msg.sent` | UnreadCountConsumer |
| `flybook.dlq` | `dlq` | DeadLetterConsumer |

---

## 三、新增文件清单

### 配置类（3 个）
- `config/RabbitMQConfig.java` - Exchange、Queue、Binding 声明
- `mq/RoutingKeys.java` - 路由键常量
- `mq/QueueNames.java` - 队列名常量

### 实体类（2 个）
- `entity/MqOutbox.java` - Outbox 实体（已更新字段名）
- `entity/MqFailedMessage.java` - 失败消息实体（已更新字段名）

### 消费者（7 个，均已迁移）
- `mq/MessagePushConsumer.java` - 消息推送消费者
- `mq/MessageRevokeConsumer.java` - 消息撤回消费者
- `mq/MessageEditConsumer.java` - 消息编辑消费者
- `mq/ReactionPushConsumer.java` - 表情回应消费者
- `mq/DeadLetterConsumer.java` - 死信队列消费者
- `mq/SearchIndexConsumer.java` - 搜索索引消费者（骨架）
- `mq/UnreadCountConsumer.java` - 未读计数消费者（骨架）

### 健康检查
- `health/RabbitMQHealthIndicator.java` - RabbitMQ 健康检查

---

## 四、修改文件清单

### 1. SendMessageUseCase.java
**改动**：字段改为 exchange/routingKey
```java
MqOutbox outbox = MqOutbox.builder()
        .messageId(message.getMessageId())
        .exchange(mqExchange)
        .routingKey(RoutingKeys.MSG_SENT)
        .body(JSONUtil.toJsonStr(event))
        .status(MqOutbox.Status.PENDING)
        .retryCount(0)
        .build();
```

### 2. MessageEventProducer.java
**改动**：
- `RocketMQTemplate` → `RabbitTemplate`
- `topic:tag` → `exchange:routingKey`

### 3. application.yaml
**改动**：RocketMQ 配置 → RabbitMQ 配置
```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest

flybook:
  mq:
    exchange: flybook.message
    dlx-exchange: flybook.message.dlx
```

---

## 五、验证方式

### 1. 拓扑验证
访问 RabbitMQ Management UI：`http://localhost:15672`
- 确认 Exchange：`flybook.message`, `flybook.message.dlx`
- 确认 Queue：7 个队列
- 确认 Bindings：每个队列绑定到正确的 exchange 和 routing key

### 2. Outbox 验证（双写一致性）

```bash
# 1. 停掉 RabbitMQ
docker stop flybook-rabbitmq

# 2. 发送消息
curl -X POST http://localhost:8081/api/messages/send \
  -H "Content-Type: application/json" \
  -d '{"conversationId": 1, "senderId": 1, "msgType": 1, "content": "{\"text\":\"test\"}"}'

# 3. 检查 outbox 表
mysql> SELECT * FROM mq_outbox WHERE status = 0;
# 应该看到 PENDING 记录

# 4. 重启 RabbitMQ
docker start flybook-rabbitmq

# 5. 等待 10 秒，再次检查
mysql> SELECT * FROM mq_outbox WHERE status = 1;
# 记录应该变为 SENT
```

### 3. 幂等验证（防重复推送）

```bash
# 1. 正常发送消息

# 2. 在 RabbitMQ Management UI 手动重发该消息
# Queue: flybook.push.queue → Get Messages → Republish

# 3. 观察日志
```

**预期结果**：
```
Duplicate MQ message skipped: messageId=123456
```

### 4. 死信验证（消费失败处理）

```java
// 1. 临时修改 MessagePushConsumer.onMessage() 抛异常

// 2. 重启应用，发送消息

// 3. 观察日志（重试 5 次，指数退避 1s→2s→4s→8s→16s）

// 4. 检查 mq_failed_messages 表
mysql> SELECT * FROM mq_failed_messages WHERE resolved = 0;
```

**预期结果**：
- 消费失败后重试 5 次
- 超过最大重试次数后进入死信队列
- `mq_failed_messages` 表出现记录

---

## 六、监控指标

### 1. Outbox 表监控
```sql
-- PENDING 记录数（应该接近 0）
SELECT COUNT(*) FROM mq_outbox WHERE status = 0;

-- FAILED 记录数（应该为 0）
SELECT COUNT(*) FROM mq_outbox WHERE status = 2;
```

### 2. 失败消息监控
```sql
SELECT COUNT(*) FROM mq_failed_messages WHERE resolved = 0;
```

### 3. Redis 幂等键监控
```bash
redis-cli KEYS "mq:consumed:*" | wc -l
```

---

## 七、故障处理

### 1. Outbox 积压

**原因**：RabbitMQ 长时间不可用

**处理**：
```sql
SELECT status, COUNT(*) FROM mq_outbox GROUP BY status;
docker ps | grep rabbitmq
UPDATE mq_outbox SET status = 0, retry_count = 0 WHERE status = 2;
```

### 2. 死信消息处理

```sql
SELECT * FROM mq_failed_messages WHERE resolved = 0;
UPDATE mq_failed_messages SET resolved = 1 WHERE id = ?;
```

---

## 八、总结

RabbitMQ 迁移完成，实现了：

1. **可靠性**：Outbox Pattern 保证双写一致性
2. **幂等性**：Redis 去重防止重复推送
3. **可观测性**：死信队列持久化失败消息
4. **可扩展性**：多队列架构支持独立扩展
5. **重试机制**：Spring Retry 5 次 + 指数退避 + DLX

系统从 RocketMQ 迁移到 RabbitMQ，所有核心功能保持不变。
