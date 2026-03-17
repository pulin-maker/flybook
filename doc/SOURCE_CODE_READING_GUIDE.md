# FlyBook 源码阅读攻略

> 本文档帮助你系统性地理解 FlyBook 项目，为面试准备提供清晰的学习路径。

---

## 一、项目技术栈一览

```
┌─────────────────────────────────────────────────┐
│                    前端 (Vue)                     │
├─────────────────────────────────────────────────┤
│              WebSocket (ws://)  │  HTTP REST API │
├─────────────────────────────────────────────────┤
│  Spring Boot 2.7.3                              │
│  ├── Spring MVC        (REST Controller)        │
│  ├── Spring AMQP       (RabbitMQ 消息队列)       │
│  ├── Spring WebSocket  (实时推送)                │
│  ├── Spring Validation (参数校验 JSR-380)        │
│  ├── Spring AOP        (限流切面)                │
│  └── Spring Actuator   (健康检查)                │
├─────────────────────────────────────────────────┤
│  MyBatis-Plus 3.5  │  Redisson 3.23  │  JWT     │
├─────────────────────────────────────────────────┤
│  MySQL 8.0  │  Redis  │  RabbitMQ 3.12          │
└─────────────────────────────────────────────────┘
```

---

## 二、分层架构（最重要，先理解这个）

```
请求流入方向 →

Controller        →  Service          →  UseCase           →  Repository       →  Mapper
(接收HTTP请求)       (接口门面)           (核心业务逻辑)        (数据访问抽象)       (SQL执行)
                                           ↓
                                      EventProducer → RabbitMQ → Consumer → WebSocket推送
```

### 各层职责

| 层 | 包名 | 职责 | 面试关键词 |
|----|------|------|-----------|
| **Controller** | `controller/` | 接收请求、参数校验、调用 Service | RESTful、@Valid |
| **Service** | `service/` | 接口定义 + 薄实现，委托给 UseCase | 门面模式 |
| **UseCase** | `usecase/` | **核心业务逻辑**，一个 UseCase = 一个业务用例 | 单一职责、可测试 |
| **Repository** | `repository/` | 数据访问抽象层，封装 Mapper 调用 | 仓储模式 |
| **Mapper** | `mapper/` | MyBatis-Plus 的 DAO 层 | SQL 映射 |

> **面试话术：** "我们采用 UseCase 模式来组织业务逻辑，每个 UseCase 代表一个独立的业务用例，比如 SendMessageUseCase 专门负责发消息的完整流程。这样做的好处是每个类职责单一、易于测试，避免了 Service 层变成 God Class。"

---

## 三、推荐阅读顺序

### 第一阶段：理解骨架（Day 1）

按这个顺序看，先建立整体认知：

```
1. MainApplication.java          -- 入口，看 @EnableScheduling 等注解
2. config/WebConfig.java          -- 拦截器注册、静态资源映射
3. interceptor/TokenInterceptor   -- JWT 鉴权流程
4. common/Result.java             -- 统一响应格式
5. exception/ErrorCode.java       -- 错误码体系
6. handler/GlobalExceptionHandler -- 全局异常处理
```

**理解目标：** 一个请求从进来到返回经过了哪些环节。

```
HTTP Request
  → TraceIdFilter (生成 traceId 写入 MDC)
  → TokenInterceptor (JWT 校验 → UserContext)
  → Controller (@Valid 参数校验)
  → Service → UseCase (业务逻辑)
  → 成功: Result.ok(data) / 异常: GlobalExceptionHandler 捕获
  → HTTP Response (含 traceId)
```

### 第二阶段：核心业务 - 发消息（Day 2）

这是整个项目最核心的链路，面试必问：

```
1. controller/MessageController.java    -- send() 方法入口
2. dto/SendMsgRequest.java              -- 请求参数 + @Valid 校验
3. service/IMessageService.java         -- 接口定义
4. service/impl/MessageServiceImpl.java -- 委托给 UseCase
5. usecase/message/SendMessageUseCase.java  -- ⭐ 重点阅读
   ├── 校验会话成员身份
   ├── 校验是否被禁言
   ├── Snowflake 生成 seq 序列号
   ├── 存储消息 + 更新会话摘要（CAS 乐观锁）
   ├── 增加其他成员未读数
   ├── 写入 Outbox 表（事务内）
   └── 事务提交后发送 MQ 事件
6. mq/MessageEventProducer.java         -- RabbitMQ 发送
7. mq/MessagePushConsumer.java          -- ⭐ 消费 + WebSocket 推送
   ├── 幂等校验（Redis SETNX）
   ├── 查询会话成员
   ├── 遍历推送（大群 >20 人异步推送）
   └── 注册 ACK 待确认
```

**画出这个流程图，面试时讲清楚就赢了。**

### 第三阶段：消息可靠性保证（Day 3）

这是面试加分项，涉及分布式系统设计：

```
1. entity/MqOutbox.java                    -- Outbox 表结构
2. mapper/MqOutboxMapper.java              -- 自定义 SQL
3. scheduler/OutboxRetryScheduler.java     -- 定时扫描重试
4. mq/MessageEventProducer.java            -- 事务后发送 + Outbox 写入
5. config/RabbitMQConfig.java              -- Exchange/Queue/DLX 拓扑
6. mq/DeadLetterConsumer.java              -- 死信队列处理
7. entity/MqFailedMessage.java             -- 失败消息持久化
```

**面试话术：** "我们用 Outbox Pattern 保证消息可靠投递。在发消息的事务中同时写入 Outbox 表，事务提交后立即尝试发 MQ。如果 MQ 发送失败，定时任务每 10 秒扫描重试，最多 5 次。消费端如果处理失败，经过 5 次指数退避重试后进入死信队列，持久化到 mq_failed_messages 表供人工介入。"

### 第四阶段：实时通信 - WebSocket（Day 4）

```
1. config/WebSocketConfig.java             -- 端点注册
2. consumer/WebSocketServer.java           -- @ServerEndpoint 核心类
   ├── @OnOpen    → 注册到 SessionRegistry
   ├── @OnMessage → 处理 ping/ack/业务消息
   ├── @OnClose   → 注销
   └── @OnError   → 异常处理
3. websocket/SessionRegistry.java          -- ⭐ 本地 + Redis 混合会话管理
4. websocket/AckPendingService.java        -- ACK 确认 + 重试机制
5. scheduler/AckRetryScheduler.java        -- 定时重试未 ACK 消息
```

**面试话术：** "WebSocket 会话采用 本地 Map + Redis 的混合管理。本地 Map 存当前服务器的连接，Redis 存 userId 到 serverId 的映射，支持分布式部署时跨实例查找用户。消息推送后注册 ACK 等待，客户端 10 秒内不确认则重试，最多 3 次。"

### 第五阶段：缓存 & 分布式锁（Day 5）

```
1. cache/TwoLevelCache.java               -- ⭐ 二级缓存泛型实现
   ├── L1: Caffeine（进程内，毫秒级）
   ├── L2: Redis（分布式，ms 级）
   └── L3: DB（兜底）
2. cache/UserCacheService.java             -- 用户缓存
3. cache/ConversationCacheService.java     -- 会话缓存
4. config/CacheConfig.java                 -- Caffeine 配置
5. lock/DistributedLockService.java        -- ⭐ Redisson 分布式锁封装
6. usecase/conversation/GroupPermissionUseCase.java  -- 锁的实际使用
```

**面试话术：** "缓存采用 Caffeine + Redis 二级架构。读请求先查 Caffeine（1000 条，5 分钟 TTL），miss 后查 Redis，再 miss 才查 DB 并回填两级。Redis 异常时自动降级为 Caffeine + DB。群主转让等关键操作使用 Redisson 分布式锁防止并发冲突。"

### 第六阶段：限流 & 可观测性（Day 6）

```
1. ratelimit/RateLimit.java                -- 自定义注解
2. ratelimit/RateLimitAspect.java          -- ⭐ AOP + Redis INCR 实现
3. tracing/TraceIdFilter.java              -- TraceId 链路追踪
4. resilience/RedisHealthChecker.java      -- Redis 降级策略
5. health/RabbitMQHealthIndicator.java     -- 自定义健康检查
6. health/WebSocketHealthIndicator.java    -- WebSocket 指标
```

---

## 四、核心设计模式速查

| 模式 | 在项目中的应用 | 关键文件 |
|------|--------------|---------|
| **Outbox Pattern** | 本地消息表保证 MQ 投递可靠性 | MqOutbox + OutboxRetryScheduler |
| **二级缓存** | Caffeine L1 + Redis L2 | TwoLevelCache.java |
| **分布式锁** | Redisson 防止群管理并发 | DistributedLockService.java |
| **幂等消费** | Redis SETNX 防重复消费 | MessagePushConsumer.java |
| **AOP 限流** | 注解 + 切面 + Redis 计数器 | @RateLimit + RateLimitAspect |
| **策略降级** | Redis 不可用时跳过幂等校验 | RedisHealthChecker.java |
| **UseCase 模式** | 业务逻辑独立封装 | usecase/ 目录 |
| **仓储模式** | 数据访问抽象 | repository/ 目录 |
| **事件驱动** | MQ 解耦推送/计数等副作用 | MessageEventProducer + Consumers |
| **ACK + 重试** | 消息推送确认机制 | AckPendingService + AckRetryScheduler |

---

## 五、面试高频问题 & 对应源码

### Q1: "发送一条消息的完整链路是什么？"

```
Controller.send()
  → MessageServiceImpl.sendMessage()
    → SendMessageUseCase.execute()
      → 1. 校验成员身份 + 禁言状态
      → 2. Snowflake 生成全局递增 seq
      → 3. 构建 Message 实体，存入 DB
      → 4. CAS 更新 conversation 的 current_seq
      → 5. 批量更新其他成员 unread_count
      → 6. 写入 mq_outbox 表（同一事务）
      → 7. @TransactionSynchronization afterCommit:
           → MessageEventProducer.sendEvent()
             → RabbitTemplate.convertAndSend(exchange, routingKey, event)
               → MessagePushConsumer.onMessage()
                 → 幂等校验 (Redis SETNX)
                 → 查询会话成员列表
                 → 遍历成员，通过 SessionRegistry 推送
                 → 注册 AckPending
```

### Q2: "如何保证消息不丢？"

三层保障：
1. **生产端**：Outbox Pattern - 消息和业务数据同事务写入，定时重试兜底
2. **Broker**：RabbitMQ publisher-confirm + 持久化队列
3. **消费端**：自动 ACK + 5 次指数退避重试 + 死信队列持久化

### Q3: "如何保证消息不重复消费？"

```
MessagePushConsumer → Redis SETNX "mq:dedup:{messageId}"
  → 成功: 首次消费，处理业务
  → 失败: 重复消息，跳过
  → Redis 宕机: RedisHealthChecker 检测到降级，放行（可用性优先）
```

### Q4: "缓存一致性怎么处理？"

```
写操作: 先更新 DB → 再删除 L1 + L2 缓存（Cache-Aside）
读操作: L1 → L2 → DB → 回填 L1 + L2
L1 TTL: 5 min（短，容忍短暂不一致）
L2 TTL: 由 Redis 配置控制
Redis 宕机: 降级为 L1 + DB
```

### Q5: "限流怎么实现的？"

```
@RateLimit(permits=30, window=60)  -- 注解声明
RateLimitAspect 切面拦截:
  key = "rate_limit:{methodName}:{userId}"
  count = Redis INCR key
  if count == 1: EXPIRE key {window}秒
  if count > permits: throw BizException(RATE_LIMIT_EXCEEDED)
```

### Q6: "WebSocket 如何支持分布式？"

```
SessionRegistry:
  本地: ConcurrentHashMap<userId, Session>  -- 当前实例连接
  Redis: ws:session:{userId} → serverId     -- 全局路由表

推送流程:
  1. 先查本地 Map → 找到直接推送
  2. 本地没有 → 查 Redis 获取 serverId
  3. 如果 serverId 是当前实例 → 用户已断开，清理
  4. 如果是其他实例 → (预留跨实例转发)
```

---

## 六、目录结构脑图

```
com.bytedance
│
├── 入口层
│   ├── controller/          # REST API 入口 (4个)
│   ├── consumer/            # WebSocket 端点
│   └── mq/                  # MQ 消费者 (7个)
│
├── 业务层
│   ├── service/             # 接口 + 薄实现
│   ├── usecase/             # ⭐ 核心业务逻辑 (11个)
│   └── event/               # 领域事件定义
│
├── 数据层
│   ├── entity/              # 数据库实体 (7个)
│   ├── repository/          # 仓储接口 + 实现
│   ├── mapper/              # MyBatis Mapper
│   ├── dto/                 # 请求 DTO
│   └── vo/                  # 响应 VO
│
├── 基础设施
│   ├── config/              # 配置类 (14个)
│   ├── cache/               # 二级缓存
│   ├── lock/                # 分布式锁
│   ├── ratelimit/           # 限流
│   ├── tracing/             # 链路追踪
│   ├── resilience/          # 降级策略
│   ├── health/              # 健康检查
│   ├── scheduler/           # 定时任务
│   └── websocket/           # WS 会话管理
│
├── 异常体系
│   ├── exception/           # BizException + ErrorCode
│   └── handler/             # 全局异常处理器
│
└── 工具
    └── utils/               # JWT、加密、Redis、UserContext
```

---

## 七、学习时间规划建议

| 天数 | 阅读内容 | 面试能讲的点 |
|------|---------|-------------|
| Day 1 | 骨架：入口 → 拦截器 → 异常处理 | 分层架构、统一响应、错误码体系 |
| Day 2 | 发消息全链路 | UseCase 模式、Snowflake 序列号、事件驱动 |
| Day 3 | 消息可靠性 | Outbox Pattern、死信队列、幂等消费 |
| Day 4 | WebSocket 实时通信 | 会话管理、ACK 机制、心跳保活 |
| Day 5 | 缓存 + 分布式锁 | 二级缓存、Redisson、降级策略 |
| Day 6 | 限流 + 可观测 | AOP 限流、TraceId、Actuator 健康检查 |
| Day 7 | 群权限 + 消息编辑/撤回 | RBAC、业务规则、时间窗口控制 |
