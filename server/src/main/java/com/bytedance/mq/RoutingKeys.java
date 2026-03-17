package com.bytedance.mq;

/**
 * RabbitMQ 路由键常量
 */
public final class RoutingKeys {
    public static final String MSG_SENT = "msg.sent";
    public static final String MSG_REVOKED = "msg.revoked";
    public static final String MSG_EDITED = "msg.edited";
    public static final String REACTION_CHANGED = "reaction.changed";
    public static final String DLQ = "dlq";

    private RoutingKeys() {}
}
