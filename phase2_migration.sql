-- ================================================
-- FlyBook Phase 2 数据库迁移脚本
-- ================================================

-- 2.3 消息编辑：新增编辑相关字段
ALTER TABLE messages
ADD COLUMN is_edited TINYINT DEFAULT 0 COMMENT '是否已编辑 0=否 1=是',
ADD COLUMN edited_content JSON DEFAULT NULL COMMENT '编辑后的内容',
ADD COLUMN edit_time TIMESTAMP DEFAULT NULL COMMENT '最后编辑时间';

-- 2.4 群组权限：conversation_members 增加禁言字段
ALTER TABLE conversation_members
ADD COLUMN is_muted TINYINT DEFAULT 0 COMMENT '是否被禁言 0=否 1=是';

-- 性能索引
CREATE INDEX idx_cm_user_id ON conversation_members (user_id);
CREATE INDEX idx_msg_conversation_seq ON messages (conversation_id, seq);
CREATE INDEX idx_reaction_message ON message_reactions (message_id);
