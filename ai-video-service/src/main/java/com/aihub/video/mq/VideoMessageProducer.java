package com.aihub.video.mq;

import com.aihub.video.entity.VideoTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void sendVideoTask(VideoTask task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            rabbitTemplate.convertAndSend("ai.video.queue", json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("发送视频任务消息失败", e);
        }
    }
}
