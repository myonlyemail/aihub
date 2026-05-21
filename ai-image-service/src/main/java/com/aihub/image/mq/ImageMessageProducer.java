package com.aihub.image.mq;

import com.aihub.image.entity.ImageTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void sendImageTask(ImageTask task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            rabbitTemplate.convertAndSend("ai.image.queue", json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("发送图片任务消息失败", e);
        }
    }
}
