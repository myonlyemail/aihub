package com.aihub.video.mq;

import com.aihub.video.entity.VideoTask;
import com.aihub.video.service.impl.VideoServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTaskConsumer {

    private final VideoServiceImpl videoService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "ai.video.queue")
    public void handleVideoTask(byte[] message) {
        try {
            VideoTask task = objectMapper.readValue(new String(message), VideoTask.class);
            log.info("收到视频生成任务: taskId={}, model={}, title={}", task.getId(), task.getModel(), task.getTitle());
            videoService.processVideoTask(task.getId());
        } catch (Exception e) {
            log.error("视频任务处理失败", e);
        }
    }
}
