# FlyBook Apifox 全链路测试手册

> 按照顺序执行以下测试流程，可完整覆盖所有后端功能。

---

## 一、环境准备

| 服务 | 地址 | 确认方式 |
|------|------|---------|
| Spring Boot | http://localhost:8081 | GET `/actuator/health` 返回 `UP` |
| MySQL | 127.0.0.1:3306 | flybook 数据库存在 |
| Redis | 127.0.0.1:6379 | `redis-cli ping` 返回 PONG |
| RabbitMQ | 127.0.0.1:5672 | 管理界面 http://localhost:15672 |
| WebSocket | ws://localhost:8081/ws/{userId} | 用 Apifox WebSocket 连接 |

**Apifox 全局设置：**
- Base URL: `http://localhost:8081`
- 在 Header 中配置 `token: {{token}}`（登录后自动获取）

---

## 二、测试流程总览

```
用户注册/登录 → 创建会话 → 邀请成员 → 发送消息 → 消息同步
    → 消息撤回 → 消息编辑 → 表情回应 → 群权限管理 → WebSocket 推送
```

---

## 三、模块一：用户 (User)

### 3.1 用户登录

```
POST /api/users/login
Content-Type: application/json

{
  "username": "ZhangSan",
  "password": "123456"
}
```

**验证点：**
- [ ] 返回 `code: 0`，data 中包含 `token` 和 `userInfo`
- [ ] 将 token 保存为 Apifox 环境变量 `{{token}}`
- [ ] 错误用户名 → `code: 10001` (USER_NOT_FOUND)
- [ ] 错误密码 → `code: 10002` (PASSWORD_WRONG)

### 3.2 获取用户列表

```
GET /api/users/list
Header: token: {{token}}
```

**验证点：**
- [ ] 返回所有用户（userId, username, avatarUrl）
- [ ] 不应包含 password 字段

---

## 四、模块二：会话 (Conversation)

### 4.1 创建群聊

```
POST /api/conversations/create
Header: token: {{token}}

{
  "name": "测试群组",
  "type": 2,
  "ownerId": 1001,
  "targetUserIds": [1002, 1003]
}
```

**验证点：**
- [ ] 返回 conversationId
- [ ] 数据库 conversation_members 中创建者 role=2（群主）
- [ ] 其他成员 role=0（普通成员）

### 4.2 创建单聊

```
POST /api/conversations/create

{
  "name": "单聊测试",
  "type": 1,
  "ownerId": 1001,
  "targetUserIds": [1002]
}
```

**验证点：**
- [ ] type=1 单聊创建成功
- [ ] 再次添加第3人时报错 `code: 20003` (DM_MEMBER_LIMIT)

### 4.3 邀请成员

```
POST /api/conversations/members/add
Header: token: {{token}}

{
  "conversationId": {{conversationId}},
  "inviterId": 1001,
  "targetUserIds": [1004, 1005]
}
```

**验证点：**
- [ ] 成员添加成功
- [ ] 重复添加已有成员 → 自动去重，不报错
- [ ] 群内会收到系统消息 "XXX 邀请 YYY 加入了群聊"

### 4.4 获取会话列表

```
GET /api/conversations/list
Header: token: {{token}}
```

**验证点：**
- [ ] 返回当前用户所有会话
- [ ] 每个会话包含 lastMsgContent、unreadCount、isTop 等字段
- [ ] 置顶会话排在前面

### 4.5 清除未读数

```
POST /api/conversations/unread/clear?conversationId={{conversationId}}
Header: token: {{token}}
```

**验证点：**
- [ ] unread_count 归零
- [ ] 重复调用幂等，不报错

### 4.6 清除全部未读

```
POST /api/conversations/unread/clear-all
Header: token: {{token}}
```

### 4.7 置顶/取消置顶

```
POST /api/conversations/top
Header: token: {{token}}

{
  "conversationId": {{conversationId}},
  "isTop": true
}
```

**验证点：**
- [ ] is_top 字段变为 1
- [ ] 再次设置 isTop=false 后恢复为 0

---

## 五、模块三：消息 (Message)

### 5.1 发送文本消息

```
POST /api/messages/send
Header: token: {{token}}

{
  "conversationId": {{conversationId}},
  "msgType": 1,
  "text": "Hello, 这是一条测试消息"
}
```

**验证点：**
- [ ] 返回完整 Message 对象（messageId, seq, content, createdTime）
- [ ] RabbitMQ 管理界面看到消息经过 `flybook.message` exchange
- [ ] WebSocket 在线用户收到推送
- [ ] conversation 的 current_seq 递增
- [ ] 其他成员 unread_count +1

### 5.2 发送图片消息

```
POST /api/messages/send

{
  "conversationId": {{conversationId}},
  "msgType": 2,
  "content": "{\"url\":\"http://localhost:8081/files/test.jpg\",\"width\":800,\"height\":600}"
}
```

### 5.3 发送带 @提及 的消息

```
POST /api/messages/send

{
  "conversationId": {{conversationId}},
  "msgType": 1,
  "text": "@LiSi 你看一下这个",
  "mentionUserIds": [1002]
}
```

**验证点：**
- [ ] 返回的 mentions 字段包含 [1002]
- [ ] WebSocket 推送中包含 mentionUserIds

### 5.4 发送回复消息

```
POST /api/messages/send

{
  "conversationId": {{conversationId}},
  "msgType": 1,
  "text": "这是一条回复",
  "quoteId": {{previousMessageId}}
}
```

**验证点：**
- [ ] 返回的 quoteId 字段等于引用的消息 ID

### 5.5 消息同步

```
GET /api/messages/sync?conversationId={{conversationId}}&afterSeq=0
Header: token: {{token}}
```

**验证点：**
- [ ] 返回 seq > afterSeq 的所有消息
- [ ] 最多返回 100 条
- [ ] 按 seq 升序排列

### 5.6 获取单条消息

```
GET /api/messages/{{messageId}}
Header: token: {{token}}
```

### 5.7 消息撤回（2分钟内）

```
POST /api/messages/revoke
Header: token: {{token}}

{
  "messageId": {{messageId}}
}
```

**验证点：**
- [ ] 发送者 2 分钟内撤回 → 成功，is_revoked=1
- [ ] 发送者超过 2 分钟 → `code: 30002` (REVOKE_TIMEOUT)
- [ ] 管理员/群主撤回他人消息 → 无时间限制，成功
- [ ] 普通成员撤回他人消息 → `code: 30003` (NO_PERMISSION_REVOKE)
- [ ] 重复撤回同一条 → 幂等，不报错
- [ ] WebSocket 收到 MSG_REVOKED 通知

### 5.8 消息编辑（24小时内）

```
POST /api/messages/edit
Header: token: {{token}}

{
  "messageId": {{messageId}},
  "newContent": "这是编辑后的内容"
}
```

**验证点：**
- [ ] 仅发送者可编辑
- [ ] 24h 内 → 成功，is_edited=1, edited_content 有值
- [ ] 超过 24h → `code: 30005` (EDIT_TIMEOUT)
- [ ] 编辑已撤回消息 → `code: 30006` (EDIT_REVOKED_MSG)
- [ ] 他人编辑 → `code: 30007` (NO_PERMISSION_EDIT)
- [ ] WebSocket 收到 MSG_EDITED 通知

### 5.9 表情回应

```
POST /api/messages/reactions/toggle
Header: token: {{token}}

{
  "messageId": {{messageId}},
  "reactionType": "thumbsup"
}
```

**验证点：**
- [ ] 第一次调用 → 返回 true（添加）
- [ ] 第二次调用相同参数 → 返回 false（取消）
- [ ] WebSocket 收到 REACTION_CHANGED 通知

### 5.10 获取表情回应列表

```
GET /api/messages/reactions?messageId={{messageId}}
Header: token: {{token}}
```

---

## 六、模块四：群权限管理

> 以下测试需要准备三个角色：群主(role=2)、管理员(role=1)、普通成员(role=0)

### 6.1 设置管理员（群主操作）

```
POST /api/conversations/members/set-admin
Header: token: {{ownerToken}}

{
  "conversationId": {{conversationId}},
  "targetUserId": 1002,
  "isAdmin": true
}
```

**验证点：**
- [ ] 群主设置 → 成功，target.role 变为 1
- [ ] 管理员尝试设置 → `code: 40001` (PERMISSION_DENIED)
- [ ] isAdmin=false → 取消管理员，role 恢复为 0

### 6.2 踢人

```
POST /api/conversations/members/kick
Header: token: {{ownerToken}}

{
  "conversationId": {{conversationId}},
  "targetUserId": 1004
}
```

**验证点：**
- [ ] 群主踢任何人 → 成功
- [ ] 管理员踢普通成员 → 成功
- [ ] 管理员踢管理员 → `code: 40001` (PERMISSION_DENIED)
- [ ] 普通成员踢人 → `code: 40001`

### 6.3 转让群主

```
POST /api/conversations/transfer-owner
Header: token: {{ownerToken}}

{
  "conversationId": {{conversationId}},
  "newOwnerId": 1002
}
```

**验证点：**
- [ ] 原群主 role 变为 0，新群主 role 变为 2
- [ ] conversations.owner_id 更新
- [ ] 使用分布式锁保证并发安全

### 6.4 解散群聊

```
POST /api/conversations/dissolve?conversationId={{conversationId}}
Header: token: {{ownerToken}}
```

**验证点：**
- [ ] 所有成员被移除
- [ ] 仅群主可操作

### 6.5 禁言

```
POST /api/conversations/members/mute
Header: token: {{ownerToken}}

{
  "conversationId": {{conversationId}},
  "targetUserId": 1004,
  "isMuted": true
}
```

**验证点：**
- [ ] 被禁言后发消息 → `code: 40004` (USER_IS_MUTED)
- [ ] 群主/管理员可以禁言低权限成员
- [ ] 不能禁言同级或上级

---

## 七、模块五：WebSocket 测试

### 7.1 连接

在 Apifox 中新建 WebSocket 连接：
```
ws://localhost:8081/ws/1001
```

### 7.2 心跳

发送：
```
ping
```

期望收到：
```
pong
```

### 7.3 消息推送验证

1. 用户 A（userId=1001）连接 WebSocket
2. 用户 B 向 A 所在的群发送消息
3. A 的 WebSocket 应收到推送，格式：
```json
{
  "type": "MSG_SENT",
  "messageId": 123,
  "conversationId": 8,
  "senderId": 1002,
  "content": {...},
  "seq": 11
}
```

### 7.4 ACK 机制

收到消息后发送 ACK：
```json
{"type": "ack", "messageId": 123}
```

**验证点：**
- [ ] 不发 ACK → 10 秒后重试推送（最多 3 次）
- [ ] 发送 ACK → 不再重推

### 7.5 撤回/编辑/表情通知

触发对应操作后，WebSocket 应收到：
- 撤回：`{"type": "MSG_REVOKED", ...}`
- 编辑：`{"type": "MSG_EDITED", ...}`
- 表情：`{"type": "REACTION_CHANGED", ...}`

---

## 八、模块六：高可用 & 非功能测试

### 8.1 限流测试

快速连续调用发消息接口 31 次（限制 30 次/60 秒）：

```
POST /api/messages/send  (循环 31 次)
```

**验证点：**
- [ ] 前 30 次成功
- [ ] 第 31 次 → `code: 40002` (RATE_LIMIT_EXCEEDED)

### 8.2 TraceId 链路追踪

```
GET /api/conversations/list
Header: token: {{token}}
```

**验证点：**
- [ ] 响应 JSON 中包含 `traceId` 字段
- [ ] 后端日志中同一请求的所有日志行包含相同 traceId

### 8.3 参数校验 (@Valid)

发送缺少必填字段的请求：
```
POST /api/messages/send
{ }
```

**验证点：**
- [ ] 返回 `code: 40003` (PARAM_INVALID)
- [ ] msg 中包含具体字段的校验信息

### 8.4 健康检查

```
GET /actuator/health
```

**验证点：**
- [ ] 整体 status=UP
- [ ] 包含 rabbit、redis、websocket 子指标
- [ ] 停掉 Redis 后 redis 状态变为 DOWN

### 8.5 死信队列

1. 模拟消费者处理失败（可临时抛异常）
2. 重试 5 次后消息进入 `flybook.dlq`
3. `mq_failed_messages` 表中记录失败原因

---

## 九、模块七：文件上传

### 9.1 上传图片

```
POST /api/upload
Content-Type: multipart/form-data
Header: token: {{token}}

file: [选择图片文件]
```

**验证点：**
- [ ] 返回 `{url: "http://localhost:8081/files/xxx.jpg"}`
- [ ] 通过返回的 URL 可以直接访问图片

---

## 十、推荐测试执行顺序

| 步骤 | 操作 | 用户 |
|------|------|------|
| 1 | 登录 ZhangSan → 保存 token | ZhangSan |
| 2 | 登录 LiSi → 保存 token2 | LiSi |
| 3 | 创建群聊（ZhangSan 为群主） | ZhangSan |
| 4 | 邀请 LiSi、WangWu 加入 | ZhangSan |
| 5 | LiSi 连接 WebSocket | LiSi |
| 6 | ZhangSan 发送文本消息 | ZhangSan |
| 7 | 验证 LiSi WebSocket 收到推送 | LiSi |
| 8 | LiSi 发送 ACK | LiSi |
| 9 | ZhangSan 发送带 @LiSi 的消息 | ZhangSan |
| 10 | LiSi 回复消息（quoteId） | LiSi |
| 11 | ZhangSan 撤回自己的消息 | ZhangSan |
| 12 | LiSi 编辑自己的消息 | LiSi |
| 13 | LiSi 对消息添加表情 | LiSi |
| 14 | ZhangSan 设置 LiSi 为管理员 | ZhangSan |
| 15 | LiSi 踢 WangWu | LiSi |
| 16 | ZhangSan 禁言 LiSi | ZhangSan |
| 17 | LiSi 发消息 → 被拒绝 | LiSi |
| 18 | 快速发消息测试限流 | ZhangSan |
| 19 | 检查 actuator/health | - |
| 20 | 检查响应中的 traceId | - |
