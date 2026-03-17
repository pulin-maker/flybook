package com.bytedance.mq;

/**
 * RabbitMQ 队列名常量
 */
public final class QueueNames {
    public static final String PUSH = "flybook.push.queue";
    public static final String REVOKE = "flybook.revoke.queue";
    public static final String EDIT = "flybook.edit.queue";
    public static final String REACTION = "flybook.reaction.queue";
    public static final String SEARCH = "flybook.search.queue";
    public static final String UNREAD = "flybook.unread.queue";
    public static final String DLQ = "flybook.dlq";

    private QueueNames() {}
}
