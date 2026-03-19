package com.bytedance.infrastructure.config;

import com.bytedance.infrastructure.mq.QueueNames;
import com.bytedance.infrastructure.mq.RoutingKeys;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 拓扑配置
 * 声明 Exchange、Queue、Binding 和重试容器工厂
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "flybook.message";
    public static final String DLX_EXCHANGE = "flybook.message.dlx";
    public static final String WS_FANOUT_EXCHANGE = "flybook.ws.fanout";

    // ==================== Exchange ====================

    @Bean
    public TopicExchange flybookExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange wsFanoutExchange() {
        return new FanoutExchange(WS_FANOUT_EXCHANGE, true, false);
    }

    // ==================== Queues ====================

    private Queue buildQueue(String name) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", RoutingKeys.DLQ);
        return new Queue(name, true, false, false, args);
    }

    @Bean
    public Queue pushQueue() {
        return buildQueue(QueueNames.PUSH);
    }

    @Bean
    public Queue revokeQueue() {
        return buildQueue(QueueNames.REVOKE);
    }

    @Bean
    public Queue editQueue() {
        return buildQueue(QueueNames.EDIT);
    }

    @Bean
    public Queue reactionQueue() {
        return buildQueue(QueueNames.REACTION);
    }

    @Bean
    public Queue searchQueue() {
        return buildQueue(QueueNames.SEARCH);
    }

    @Bean
    public Queue unreadQueue() {
        return buildQueue(QueueNames.UNREAD);
    }

    @Bean
    public Queue dlqQueue() {
        return new Queue(QueueNames.DLQ, true);
    }

    /** 每个实例独立的匿名队列（auto-delete，实例停止自动删除） */
    @Bean
    public Queue wsFanoutQueue() {
        return new AnonymousQueue();
    }

    // ==================== Bindings ====================

    @Bean
    public Binding pushBinding() {
        return BindingBuilder.bind(pushQueue()).to(flybookExchange()).with(RoutingKeys.MSG_SENT);
    }

    @Bean
    public Binding revokeBinding() {
        return BindingBuilder.bind(revokeQueue()).to(flybookExchange()).with(RoutingKeys.MSG_REVOKED);
    }

    @Bean
    public Binding editBinding() {
        return BindingBuilder.bind(editQueue()).to(flybookExchange()).with(RoutingKeys.MSG_EDITED);
    }

    @Bean
    public Binding reactionBinding() {
        return BindingBuilder.bind(reactionQueue()).to(flybookExchange()).with(RoutingKeys.REACTION_CHANGED);
    }

    @Bean
    public Binding searchBinding() {
        return BindingBuilder.bind(searchQueue()).to(flybookExchange()).with(RoutingKeys.MSG_SENT);
    }

    @Bean
    public Binding searchEditBinding() {
        return BindingBuilder.bind(searchQueue()).to(flybookExchange()).with(RoutingKeys.MSG_EDITED);
    }

    @Bean
    public Binding searchRevokeBinding() {
        return BindingBuilder.bind(searchQueue()).to(flybookExchange()).with(RoutingKeys.MSG_REVOKED);
    }

    @Bean
    public Binding unreadBinding() {
        return BindingBuilder.bind(unreadQueue()).to(flybookExchange()).with(RoutingKeys.MSG_SENT);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlxExchange()).with(RoutingKeys.DLQ);
    }

    @Bean
    public Binding wsFanoutBinding() {
        return BindingBuilder.bind(wsFanoutQueue()).to(wsFanoutExchange());
    }

    // ==================== Retry Container Factory ====================

    @Bean("retryContainerFactory")
    public SimpleRabbitListenerContainerFactory retryContainerFactory(ConnectionFactory connectionFactory,
                                                                      RabbitTemplate rabbitTemplate) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setPrefetchCount(10);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);

        // 重试策略：最多 5 次，指数退避 1s → 2s → 4s → 8s → 16s
        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(5);
        retryTemplate.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(16000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
                .stateless()
                .retryOperations(retryTemplate)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, DLX_EXCHANGE, RoutingKeys.DLQ))
                .build());

        return factory;
    }
}
