package com.aihub.image.mq;

import com.aihub.image.entity.ImageTask;
import com.aihub.image.service.impl.ImageServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageTaskConsumer {

    private final ImageServiceImpl imageService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "ai.image.queue")
    public void handleImageTask(byte[] message) {
        try {
            ImageTask task = objectMapper.readValue(new String(message), ImageTask.class);
            log.info("收到图片生成任务: taskId={}, model={}, prompt={}", task.getId(), task.getModel(), task.getPrompt());
            imageService.processImageTask(task.getId());
        } catch (Exception e) {
            log.error("图片任务处理失败", e);
        }
    }
}
