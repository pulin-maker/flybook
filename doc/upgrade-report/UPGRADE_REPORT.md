# FlyBook 企业级升级报告

> 升级时间：2026-03-16
> 升级范围：Phase 1 ~ Phase 4
> 目标：将 FlyBook 从 Demo 级别升级到对标飞书（Lark）的企业级 IM 系统

---

## 一、升级概览

本次升级覆盖 4 个阶段，共涉及 **新建约 40 个文件**、**修改约 25 个文件**，新增 **5 个 Maven 依赖**，涵盖核心功能、高可用、高性能三大方向。

| 阶段 | 目标 | 状态 |
|------|------|------|
| Phase 1 | MQ 企业级优化 | ✅ 已完成 |
| Phase 2 | 核心功能（异常处理 / 消息生命周期 / RBAC / 缓存） | ✅ 已完成 |
| Phase 3 | 高可用（分布式锁 / 限流 / 链路追踪 / 降级 / 健康检查） | ✅ 已完成 |
| Phase 4 | 高性能（N+1 修复 / 异步推送 / 连接池调优） | ✅ 已完成 |

---

## 二、架构设计

### 2.1 整体分层

```
┌─────────────────────────────────────────────────┐
│                  Controller 层                    │
│  @Valid 参数校验 / @RateLimit 限流 / TraceId 追踪   │
├─────────────────────────────────────────────────┤
│                   Service 层                      │
│         Facade 门面，协调 UseCase                   │
├─────────────────────────────────────────────────┤
│                  UseCase 层                       │
│  核心业务逻辑 / @Transactional / 分布式锁           │
├─────────────────────────────────────────────────┤
│                 Repository 层                     │
│       数据访问抽象（便于替换数据源）                   │
├─────────────────────────────────────────────────┤
│                  Mapper 层                        │
│         MyBatis-Plus BaseMapper                   │
└─────────────────────────────────────────────────┘
```

### 2.2 消息推送链路

```
用户发消息
  │
  ▼
SendMessageUseCase（@Transactional）
  ├── 1. 校验成员资格 + 禁言状态
  ├── 2. Snowflake 生成 seq
  ├── 3. 写入 messages 表
  ├── 4. CAS 更新会话摘要 → evict 会话缓存
  ├── 5. 更新未读数
  ├── 6. 写入 mq_outbox 表（Outbox Pattern，同一事务）
  └── 7. 事务提交后 → RocketMQ 异步发送
          │
          ▼
    RocketMQ Topic（Tag 路由）
     ├── MSG_SENT     → MessagePushConsumer（幂等 + 并行推送 WebSocket）
     ├── MSG_REVOKED   → MessageRevokeConsumer（推送撤回通知）
     ├── MSG_EDITED    → MessageEditConsumer（推送编辑通知）
     ├── REACTION_CHANGED → ReactionPushConsumer（推送表情变更）
     └── DLQ           → DeadLetterConsumer（死信持久化）
```

### 2.3 二级缓存架构

```
请求
  │
  ▼
Caffeine (L1，进程内)        命中 → 直接返回
  │ 未命中
  ▼
Redis (L2，跨进程共享)        命中 → 回填 L1 → 返回
  │ 未命中
  ▼
MySQL (DB)                   查到 → 写入 L1 + L2 → 返回
```

| 缓存对象 | L1 容量 | L1 TTL | L2 TTL |
|----------|---------|--------|--------|
| 用户信息 | 1000 条 | 5 分钟 | 1 小时 |
| 会话信息 | 500 条 | 3 分钟 | 30 分钟 |

---

## 三、Phase 1 — MQ 企业级优化

### 3.1 Outbox Pattern（本地消息表）

**问题**：MySQL 事务和 MQ 发送是两个独立操作，可能出现「DB 写入成功但 MQ 发送失败」导致消息丢失。

**方案**：在同一个数据库事务内，同时写入业务表和 `mq_outbox` 表，事务提交后再异步发送 MQ。发送失败的消息由 `OutboxRetryScheduler` 定时补偿重发。

```
新建文件：
├── entity/MqOutbox.java            — Outbox 实体（PENDING / SENT / FAILED）
├── entity/MqFailedMessage.java     — 失败消息实体
├── mapper/MqOutboxMapper.java      — CAS 更新 + 批量查询
├── mapper/MqFailedMessageMapper.java
└── scheduler/OutboxRetryScheduler.java — 每 10s 扫描 PENDING 记录重发
```

### 3.2 消费端幂等

**方案**：`Redis SET NX` + 24 小时 TTL，消费前先检查 `mq:consumed:{messageId}` 是否已存在。

### 3.3 死信队列

**方案**：`maxReconsumeTimes = 5`，超过重试次数的消息进入 `%DLQ%` 队列，由 `DeadLetterConsumer` 消费并持久化到 `mq_failed_messages` 表。

### 3.4 多消费者 Tag 路由

| Tag | 消费者组 | 功能 |
|-----|---------|------|
| `MSG_SENT` | flybook-push-consumer-group | WebSocket 消息推送 |
| `MSG_REVOKED` | flybook-revoke-consumer-group | 撤回通知推送 |
| `MSG_EDITED` | flybook-edit-consumer-group | 编辑通知推送 |
| `REACTION_CHANGED` | flybook-reaction-consumer-group | 表情变更推送 |

---

## 四、Phase 2 — 核心功能

### 4.1 异常处理 + 参数校验

**原有问题**：
1. 存在两个冲突的 `@RestControllerAdvice`（`WebExceptionAdvice` 引用了错误的 `dto.Result` 类）
2. 所有业务错误都是 `throw new RuntimeException(msg)`，无错误码
3. 无 `@Valid` 参数校验

**解决方案**：

| 操作 | 文件 |
|------|------|
| 删除 | `config/WebExceptionAdvice.java`（冲突的异常处理器） |
| 新建 | `exception/ErrorCode.java` — 17 个错误码，按业务域分段编号 |
| 新建 | `exception/BizException.java` — 业务异常基类，携带 ErrorCode |
| 重写 | `handler/GlobalExceptionHandler.java` — 四层异常处理 |
| 修改 | `common/Result.java` — 新增 `fail(int code, String msg)` 重载 |

**错误码规范**：

```
10000-19999  用户相关    USER_NOT_FOUND / PASSWORD_WRONG / ...
20000-29999  会话相关    CONVERSATION_NOT_FOUND / NOT_CONVERSATION_MEMBER / ...
30000-39999  消息相关    MESSAGE_NOT_FOUND / MESSAGE_REVOKE_TIMEOUT / ...
40000-49999  权限相关    PERMISSION_DENIED / RATE_LIMIT_EXCEEDED / ...
50000+       系统错误    服务器内部错误
```

**异常迁移范围**：
- `SendMessageUseCase` — NOT_CONVERSATION_MEMBER / CONVERSATION_NOT_FOUND / USER_IS_MUTED
- `AddMembersUseCase` — CONVERSATION_NOT_FOUND / DM_MEMBER_LIMIT
- `LoginUserUseCase` — USER_NOT_FOUND / PASSWORD_WRONG
- `RegisterUserUseCase` — USERNAME_ALREADY_EXISTS
- `MessageController` — CONTENT_EMPTY / USER_ID_REQUIRED
- `ConversationController` — USER_ID_REQUIRED

### 4.2 消息撤回（Phase 2.2）

**业务规则**：
- 发送者本人：2 分钟内可撤回
- 管理员/群主（role >= 1）：不限时间
- 已撤回消息幂等跳过

**数据流**：
```
Controller POST /api/messages/revoke
  → MessageServiceImpl.revokeMessage()
    → RevokeMessageUseCase.execute()
      ├── 查消息 → 权限检查 → 时间窗口检查
      ├── UPDATE messages SET is_revoked = 1
      ├── INSERT mq_outbox (tag=MSG_REVOKED)
      └── 事务提交后 → MQ → MessageRevokeConsumer → WebSocket 推送
```

```
新建文件：
├── usecase/message/RevokeMessageUseCase.java
├── event/MessageRevokedEvent.java
├── mq/MessageRevokeConsumer.java
└── dto/request/RevokeMsgRequest.java
```

### 4.3 消息编辑（Phase 2.3）

**业务规则**：
- 仅发送者本人可编辑
- 24 小时时间窗口
- 已撤回消息不可编辑
- 编辑后标记 is_edited=1，保留 edited_content 和 edit_time

**数据库变更**：
```sql
ALTER TABLE messages
ADD COLUMN is_edited TINYINT DEFAULT 0,
ADD COLUMN edited_content JSON DEFAULT NULL,
ADD COLUMN edit_time TIMESTAMP DEFAULT NULL;
```

```
新建文件：
├── usecase/message/EditMessageUseCase.java
├── event/MessageEditedEvent.java
├── mq/MessageEditConsumer.java
└── dto/request/EditMsgRequest.java

修改文件：
└── entity/Message.java — 新增 isEdited / editedContent / editTime 字段
```

### 4.4 RBAC 群组权限（Phase 2.4）

**角色定义（规范化后）**：

| 值 | 角色 | 权限 |
|----|------|------|
| 0 | 普通成员 | 发消息 |
| 1 | 管理员 | 踢普通成员、禁言普通成员 |
| 2 | 群主 | 所有权限（踢任何人、设管理员、转让、解散、禁言） |

**修复原有问题**：
- `CreateConversationUseCase` 群主角色从 `1` 修正为 `2`
- `AddMembersUseCase` 普通成员角色 `0` 保持不变

**权限矩阵**（操作者 vs 目标）：
```
操作者角色必须严格大于目标角色（operator.role > target.role）
```

**新增 API 端点**：

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/conversations/members/kick` | 踢出成员 |
| POST | `/api/conversations/members/set-admin` | 设置/取消管理员 |
| POST | `/api/conversations/transfer-owner` | 转让群主 |
| POST | `/api/conversations/dissolve` | 解散群聊 |
| POST | `/api/conversations/members/mute` | 禁言/解禁 |

**禁言联动**：`SendMessageUseCase` 发消息前检查 `is_muted` 状态，被禁言用户抛出 `USER_IS_MUTED`。

```
新建文件：
├── usecase/conversation/GroupPermissionUseCase.java
├── dto/request/KickMemberRequest.java
├── dto/request/SetAdminRequest.java
├── dto/request/TransferOwnerRequest.java
└── dto/request/MuteUserRequest.java
```

### 4.5 @提及 + 消息回复（Phase 2.5）

**改动链路**（全链路传递 mentionUserIds 和 quoteId）：

```
SendMsgRequest（新增 mentionUserIds / quoteId）
  → MessageController
    → IMessageService.sendMessage(... mentionUserIds, quoteId)
      → SendMessageUseCase.execute(... mentionUserIds, quoteId)
        ├── 序列化 mentionUserIds → JSON 存入 messages.mentions
        ├── quoteId 存入 messages.quote_id
        └── MessageSentEvent 携带 mentions / quoteId → MQ → Push
```

### 4.6 表情回应（Phase 2.6）

**Toggle 模式**：已存在则删除（取消），不存在则新增（添加）。

**新增 API 端点**：

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/messages/reactions/toggle` | 切换表情回应 |
| GET | `/api/messages/reactions?messageId=` | 获取回应列表 |

```
新建文件：
├── usecase/message/ToggleReactionUseCase.java
├── entity/MessageReaction.java
├── mapper/MessageReactionMapper.java
├── repository/IMessageReactionRepository.java
├── repository/impl/MessageReactionRepositoryImpl.java
├── event/ReactionChangedEvent.java
├── mq/ReactionPushConsumer.java
└── dto/request/ReactionRequest.java
```

### 4.7 二级缓存（Phase 2.7）

```
新建文件：
├── config/CacheConfig.java           — Caffeine Bean 定义
├── cache/TwoLevelCache.java          — 泛型二级缓存模板
├── cache/UserCacheService.java       — 用户信息缓存
└── cache/ConversationCacheService.java — 会话信息缓存

修改文件：
├── GetConversationListUseCase.java   — 替换内联 Redis 为 UserCacheService
└── SendMessageUseCase.java           — 更新摘要后 evict 会话缓存
```

---

## 五、Phase 3 — 高可用

### 5.1 分布式锁（Redisson）

**方案**：基于 Redisson `RLock`，封装为 `DistributedLockService`，提供 `tryLock + Supplier` 模式。

**应用场景**：
- `GroupPermissionUseCase.transferOwner()` — 防止并发转让群主
- `GroupPermissionUseCase.dissolveGroup()` — 防止并发解散

```
新建文件：
├── config/RedissonConfig.java
└── lock/DistributedLockService.java
```

### 5.2 API 限流

**方案**：`@RateLimit` 自定义注解 + AOP 切面，基于 `Redis INCR + EXPIRE` 实现固定窗口计数器，按用户维度限流。

**限流配置**：

| 接口 | 限制 |
|------|------|
| `POST /api/messages/send` | 30 次/分钟 |
| `POST /api/conversations/create` | 10 次/分钟 |

```
新建文件：
├── ratelimit/RateLimit.java       — 限流注解
└── ratelimit/RateLimitAspect.java — AOP 切面
```

### 5.3 链路追踪（TraceId）

**方案**：HTTP Filter 为每个请求生成 UUID traceId，写入 `MDC`（线程上下文），日志格式和 `Result` 响应体中均携带 traceId。

**效果**：
- 日志格式：`2026-03-16 10:00:00.000 [http-nio-8081-exec-1] [a1b2c3d4] INFO ...`
- 响应体：`{"code":0, "msg":"success", "data":{...}, "traceId":"a1b2c3d4"}`
- 响应头：`X-Trace-Id: a1b2c3d4`

```
新建文件：
└── tracing/TraceIdFilter.java

修改文件：
├── common/Result.java        — 新增 traceId 字段
└── application.yaml          — 日志 pattern 加 %X{traceId}
```

### 5.4 优雅降级

**方案**：`RedisHealthChecker` 每 10 秒 ping 一次 Redis，维护 `AtomicBoolean` 可用状态。

**降级策略**：
| 组件 | Redis 可用 | Redis 不可用 |
|------|-----------|-------------|
| MessagePushConsumer 幂等检查 | Redis SET NX 去重 | 跳过幂等检查，优先保证消息送达 |
| TwoLevelCache L2 | 正常读写 Redis | 跳过 L2，直查 DB |

```
新建文件：
└── resilience/RedisHealthChecker.java
```

### 5.5 健康检查（Spring Actuator）

**暴露端点**：`GET /actuator/health`

| 指示器 | 检查内容 |
|--------|---------|
| `RedisHealthIndicator` | PING Redis |
| `RocketMQHealthIndicator` | 检查 Producer 是否初始化 |
| `WebSocketHealthIndicator` | 上报本机在线用户数 |

```
新建文件：
├── health/RedisHealthIndicator.java
├── health/RocketMQHealthIndicator.java
└── health/WebSocketHealthIndicator.java

修改文件：
└── application.yaml — 暴露 health/info/metrics 端点
```

---

## 六、Phase 4 — 高性能

### 6.1 N+1 查询修复

**原有问题**：`GetConversationListUseCase` 中，每个单聊会话都会单独查 `conversation_members` 表获取对方用户信息，导致循环内逐条查库。

**优化后**：
1. 一次性批量查出所有单聊会话的成员：`findByConversationIds(List<Long>)`
2. 按 conversationId 分组为 Map
3. 循环内直接从 Map 取值，零 SQL

**SQL 数量对比**（假设用户有 N 个单聊会话）：

| | 优化前 | 优化后 |
|--|--------|--------|
| 查成员关系 | 1 + N | 2 (myMemberships + allDmMembers) |
| 查会话 | 1 | 1 |
| 查用户信息 | N (已有 Redis 缓存) | 最多 N 次缓存查询（L1 命中率高） |

```
修改文件：
├── repository/IConversationMemberRepository.java — 新增 findByConversationIds()
├── repository/impl/ConversationMemberRepositoryImpl.java — IN 查询实现
└── usecase/conversation/GetConversationListUseCase.java — 批量查询重构
```

### 6.2 异步推送

**方案**：大群（成员 > 20 人）启用 `CompletableFuture` 并行推送，使用专用线程池 `pushExecutor`。

**线程池配置**：
| 参数 | 值 |
|------|-----|
| corePoolSize | 8 |
| maxPoolSize | 32 |
| queueCapacity | 256 |
| rejectedHandler | CallerRunsPolicy |

```
新建文件：
└── config/AsyncConfig.java — @EnableAsync + pushExecutor Bean

修改文件：
└── mq/MessagePushConsumer.java — 条件并行推送
```

### 6.3 连接池调优

**HikariCP（MySQL）**：

| 参数 | 值 | 说明 |
|------|----|------|
| minimum-idle | 5 | 最小空闲连接 |
| maximum-pool-size | 20 | 最大连接数 |
| idle-timeout | 300000 (5min) | 空闲连接回收 |
| max-lifetime | 1800000 (30min) | 连接最大生命周期 |
| connection-timeout | 5000 (5s) | 获取连接超时 |

**Lettuce Pool（Redis）**：

| 参数 | 值 |
|------|----|
| min-idle | 4 |
| max-idle | 8 |
| max-active | 16 |
| max-wait | 3000ms |

---

## 七、数据库变更汇总

```sql
-- 消息编辑字段（新增）
ALTER TABLE messages
ADD COLUMN is_edited TINYINT DEFAULT 0 COMMENT '是否已编辑 0=否 1=是',
ADD COLUMN edited_content JSON DEFAULT NULL COMMENT '编辑后的内容',
ADD COLUMN edit_time TIMESTAMP DEFAULT NULL COMMENT '最后编辑时间';

-- 性能索引（新增）
CREATE INDEX idx_cm_user_id ON conversation_members (user_id);
CREATE INDEX idx_msg_conversation_seq ON messages (conversation_id, seq);
CREATE INDEX idx_reaction_message ON message_reactions (message_id);
```

> 注：`conversation_members.is_muted` 和 `message_reactions` 表在原始 Schema 中已存在，无需变更。

---

## 八、Maven 依赖变更

```xml
<!-- JSR-380 参数校验 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Caffeine 本地缓存 -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>

<!-- Redisson 分布式锁 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.23.5</version>
</dependency>

<!-- Spring Actuator 健康检查 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- AOP 支持（限流切面） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## 九、新建文件清单

### 异常处理（3 个）
| 文件 | 说明 |
|------|------|
| `exception/ErrorCode.java` | 17 个错误码枚举 |
| `exception/BizException.java` | 业务异常基类 |
| `handler/GlobalExceptionHandler.java` | 全局异常处理器（重写） |

### 消息生命周期（12 个）
| 文件 | 说明 |
|------|------|
| `usecase/message/RevokeMessageUseCase.java` | 消息撤回 |
| `usecase/message/EditMessageUseCase.java` | 消息编辑 |
| `usecase/message/ToggleReactionUseCase.java` | 表情切换 |
| `event/MessageRevokedEvent.java` | 撤回事件 |
| `event/MessageEditedEvent.java` | 编辑事件 |
| `event/ReactionChangedEvent.java` | 表情事件 |
| `mq/MessageRevokeConsumer.java` | 撤回消费者 |
| `mq/MessageEditConsumer.java` | 编辑消费者 |
| `mq/ReactionPushConsumer.java` | 表情消费者 |
| `dto/request/RevokeMsgRequest.java` | 撤回请求 |
| `dto/request/EditMsgRequest.java` | 编辑请求 |
| `dto/request/ReactionRequest.java` | 表情请求 |

### 表情实体层（4 个）
| 文件 | 说明 |
|------|------|
| `entity/MessageReaction.java` | 表情实体 |
| `mapper/MessageReactionMapper.java` | Mapper |
| `repository/IMessageReactionRepository.java` | Repository 接口 |
| `repository/impl/MessageReactionRepositoryImpl.java` | Repository 实现 |

### RBAC 群组权限（5 个）
| 文件 | 说明 |
|------|------|
| `usecase/conversation/GroupPermissionUseCase.java` | 群组权限核心逻辑 |
| `dto/request/KickMemberRequest.java` | 踢人请求 |
| `dto/request/SetAdminRequest.java` | 设置管理员请求 |
| `dto/request/TransferOwnerRequest.java` | 转让群主请求 |
| `dto/request/MuteUserRequest.java` | 禁言请求 |

### 二级缓存（4 个）
| 文件 | 说明 |
|------|------|
| `config/CacheConfig.java` | Caffeine Bean 配置 |
| `cache/TwoLevelCache.java` | 泛型二级缓存 |
| `cache/UserCacheService.java` | 用户缓存 |
| `cache/ConversationCacheService.java` | 会话缓存 |

### 高可用基础设施（8 个）
| 文件 | 说明 |
|------|------|
| `config/RedissonConfig.java` | Redisson 配置 |
| `lock/DistributedLockService.java` | 分布式锁服务 |
| `ratelimit/RateLimit.java` | 限流注解 |
| `ratelimit/RateLimitAspect.java` | 限流切面 |
| `tracing/TraceIdFilter.java` | 链路追踪过滤器 |
| `resilience/RedisHealthChecker.java` | Redis 健康检查 |
| `health/RedisHealthIndicator.java` | Redis 健康指示器 |
| `health/RocketMQHealthIndicator.java` | MQ 健康指示器 |

### 高性能（2 个）
| 文件 | 说明 |
|------|------|
| `config/AsyncConfig.java` | 异步线程池配置 |
| `health/WebSocketHealthIndicator.java` | WS 在线数指示器 |

---

## 十、修改文件清单

| 文件 | 改动摘要 |
|------|---------|
| `common/Result.java` | 新增 traceId 字段、`fail(int, String)` 重载 |
| `entity/Message.java` | 新增 isEdited / editedContent / editTime |
| `entity/ConversationMember.java` | 已有 isMuted 字段（数据库已存在） |
| `event/MessageSentEvent.java` | 新增 mentions / quoteId |
| `dto/SendMsgRequest.java` | 新增 mentionUserIds / quoteId / @NotNull |
| `usecase/message/SendMessageUseCase.java` | 禁言检查、mentions/quoteId、缓存 evict、参数扩展 |
| `usecase/conversation/CreateConversationUseCase.java` | 群主 role 从 1 改为 2 |
| `usecase/conversation/AddMembersUseCase.java` | 适配 SendMessageUseCase 新参数 |
| `usecase/conversation/GetConversationListUseCase.java` | N+1 修复 + 二级缓存替换 |
| `mq/MessageEventProducer.java` | 新增 sendEventAfterCommit() 通用方法 |
| `mq/MessagePushConsumer.java` | Redis 降级 + 并行推送 + mentions/quoteId |
| `service/IMessageService.java` | 新增 revoke/edit/toggleReaction/getReactions |
| `service/impl/MessageServiceImpl.java` | 实现新增方法，注入新 UseCase |
| `service/IConversationService.java` | 新增 5 个群管理方法 |
| `service/impl/ConversationServiceImpl.java` | 注入 GroupPermissionUseCase，实现群管理 |
| `controller/MessageController.java` | 新增 5 个端点、@RateLimit、@Valid |
| `controller/ConversationController.java` | 新增 5 个端点、@RateLimit、@Valid |
| `repository/IConversationMemberRepository.java` | 新增 delete/updateRole/updateMute/findByIds |
| `repository/impl/ConversationMemberRepositoryImpl.java` | 实现新增方法 |
| `repository/IConversationRepository.java` | 新增 updateOwnerId / deleteById |
| `repository/impl/ConversationRepositoryImpl.java` | 实现新增方法 |
| `repository/IMessageRepository.java` | 新增 findById / updateRevokeStatus / update |
| `repository/impl/MessageRepositoryImpl.java` | 实现新增方法 |
| `pom.xml` | 新增 5 个依赖、修复缺失的 `</dependencies>` 标签 |
| `application.yaml` | HikariCP/Lettuce 连接池、Actuator 端点、traceId 日志 |
| ~~`config/WebExceptionAdvice.java`~~ | **已删除**（冲突的异常处理器） |

---

## 十一、API 端点汇总

### 消息相关（`/api/messages`）

| 方法 | 路径 | 功能 | 限流 |
|------|------|------|------|
| POST | `/send` | 发送消息 | 30 次/分钟 |
| GET | `/sync` | 同步/拉取消息 | — |
| GET | `/{messageId}` | 获取单条消息 | — |
| POST | `/revoke` | 撤回消息 | — |
| POST | `/edit` | 编辑消息 | — |
| POST | `/reactions/toggle` | 切换表情回应 | — |
| GET | `/reactions` | 获取表情列表 | — |

### 会话相关（`/api/conversations`）

| 方法 | 路径 | 功能 | 限流 |
|------|------|------|------|
| GET | `/list` | 获取会话列表 | — |
| POST | `/create` | 创建会话 | 10 次/分钟 |
| POST | `/members/add` | 邀请成员 | — |
| POST | `/members/kick` | 踢出成员 | — |
| POST | `/members/set-admin` | 设置管理员 | — |
| POST | `/members/mute` | 禁言/解禁 | — |
| POST | `/transfer-owner` | 转让群主 | — |
| POST | `/dissolve` | 解散群聊 | — |
| POST | `/unread/clear` | 清除未读 | — |
| POST | `/unread/clear-all` | 清除全部未读 | — |
| POST | `/top` | 置顶/取消置顶 | — |

### 运维端点

| 路径 | 功能 |
|------|------|
| `GET /actuator/health` | 健康检查（Redis / MQ / WebSocket） |
| `GET /actuator/info` | 应用信息 |
| `GET /actuator/metrics` | 性能指标 |

---

## 十二、验证清单

| 功能 | 验证方式 |
|------|---------|
| 异常处理 | 发送缺少参数的请求 → `{"code":40003, "msg":"参数校验失败:xxx", "traceId":"..."}` |
| 消息撤回 | 发消息 → 调 `/revoke` → WebSocket 收到 `{"type":"revoke"}` |
| 消息编辑 | 发消息 → 调 `/edit` → WebSocket 收到 `{"type":"edit"}` |
| 群权限 | 非管理员踢人 → `{"code":40001, "msg":"权限不足"}` |
| 限流 | 1 分钟内发 31 条消息 → `{"code":40002, "msg":"请求过于频繁"}` |
| 链路追踪 | 任意请求 → 响应头 `X-Trace-Id` + 响应体 `traceId` |
| 二级缓存 | 首次查会话列表查 DB；再次查命中 Caffeine（日志无 SQL） |
| N+1 修复 | 打开 SQL 日志，查会话列表只产生 3 条 SQL |
| 健康检查 | `GET /actuator/health` → 显示 Redis / MQ / WebSocket 状态 |
| 禁言 | 被禁言用户发消息 → `{"code":40004, "msg":"您已被禁言"}` |
