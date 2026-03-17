-- =============================================
-- FlyBook ShardingSphere 分库分表建表脚本
-- 2 库 × 2 表 = 4 分片，分片键: conversation_id
-- =============================================

-- 创建分片库
CREATE DATABASE IF NOT EXISTS flybook_msg_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS flybook_msg_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- =============================================
-- flybook_msg_0
-- =============================================
USE flybook_msg_0;

CREATE TABLE IF NOT EXISTS messages_0 (
    message_id      BIGINT       NOT NULL COMMENT '雪花ID（MyBatis-Plus ASSIGN_ID）',
    conversation_id BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    seq             BIGINT       NOT NULL COMMENT '会话内序列号',
    quote_id        BIGINT       DEFAULT 0 COMMENT '引用的消息ID',
    msg_type        TINYINT      NOT NULL COMMENT '1=Text, 2=Image, 3=Video, 4=File, 5=TodoCard',
    content         JSON         NOT NULL COMMENT '消息内容',
    mentions        JSON         DEFAULT NULL COMMENT '被@用户ID列表',
    is_revoked      TINYINT      DEFAULT 0 COMMENT '是否已撤回',
    is_edited       TINYINT      DEFAULT 0 COMMENT '是否已编辑',
    edited_content  JSON         DEFAULT NULL COMMENT '编辑后内容',
    edit_time       TIMESTAMP    DEFAULT NULL COMMENT '最后编辑时间',
    created_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_conv_seq (conversation_id, seq),
    KEY idx_conv_time (conversation_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS messages_1 LIKE messages_0;

CREATE TABLE IF NOT EXISTS message_reactions_0 (
    id              BIGINT       NOT NULL COMMENT '雪花ID',
    conversation_id BIGINT       NOT NULL COMMENT '分片键（冗余字段）',
    message_id      BIGINT       NOT NULL COMMENT '关联消息ID',
    user_id         BIGINT       NOT NULL COMMENT '操作用户ID',
    reaction_type   VARCHAR(32)  NOT NULL COMMENT '表情代码',
    created_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user_react (message_id, user_id, reaction_type),
    KEY idx_conv_id (conversation_id),
    KEY idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS message_reactions_1 LIKE message_reactions_0;

-- =============================================
-- flybook_msg_1
-- =============================================
USE flybook_msg_1;

CREATE TABLE IF NOT EXISTS messages_0 (
    message_id      BIGINT       NOT NULL COMMENT '雪花ID（MyBatis-Plus ASSIGN_ID）',
    conversation_id BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    seq             BIGINT       NOT NULL COMMENT '会话内序列号',
    quote_id        BIGINT       DEFAULT 0 COMMENT '引用的消息ID',
    msg_type        TINYINT      NOT NULL COMMENT '1=Text, 2=Image, 3=Video, 4=File, 5=TodoCard',
    content         JSON         NOT NULL COMMENT '消息内容',
    mentions        JSON         DEFAULT NULL COMMENT '被@用户ID列表',
    is_revoked      TINYINT      DEFAULT 0 COMMENT '是否已撤回',
    is_edited       TINYINT      DEFAULT 0 COMMENT '是否已编辑',
    edited_content  JSON         DEFAULT NULL COMMENT '编辑后内容',
    edit_time       TIMESTAMP    DEFAULT NULL COMMENT '最后编辑时间',
    created_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_conv_seq (conversation_id, seq),
    KEY idx_conv_time (conversation_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS messages_1 LIKE messages_0;

CREATE TABLE IF NOT EXISTS message_reactions_0 (
    id              BIGINT       NOT NULL COMMENT '雪花ID',
    conversation_id BIGINT       NOT NULL COMMENT '分片键（冗余字段）',
    message_id      BIGINT       NOT NULL COMMENT '关联消息ID',
    user_id         BIGINT       NOT NULL COMMENT '操作用户ID',
    reaction_type   VARCHAR(32)  NOT NULL COMMENT '表情代码',
    created_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user_react (message_id, user_id, reaction_type),
    KEY idx_conv_id (conversation_id),
    KEY idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS message_reactions_1 LIKE message_reactions_0;
