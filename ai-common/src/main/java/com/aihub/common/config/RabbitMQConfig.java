package com.aihub.common.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.core.Queue")
public class RabbitMQConfig {

    public static final String CHAT_QUEUE = "ai.chat.queue";
    public static final String IMAGE_QUEUE = "ai.image.queue";
    public static final String VIDEO_QUEUE = "ai.video.queue";
    public static final String VOICE_QUEUE = "ai.voice.queue";
    public static final String BILLING_QUEUE = "ai.billing.queue";
    public static final String AUDIT_QUEUE = "ai.audit.queue";

    @Bean
    public Queue chatQueue() {
        return new Queue(CHAT_QUEUE, true);
    }

    @Bean
    public Queue imageQueue() {
        return new Queue(IMAGE_QUEUE, true);
    }

    @Bean
    public Queue videoQueue() {
        return new Queue(VIDEO_QUEUE, true);
    }

    @Bean
    public Queue voiceQueue() {
        return new Queue(VOICE_QUEUE, true);
    }

    @Bean
    public Queue billingQueue() {
        return new Queue(BILLING_QUEUE, true);
    }

    @Bean
    public Queue auditQueue() {
        return new Queue(AUDIT_QUEUE, true);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
