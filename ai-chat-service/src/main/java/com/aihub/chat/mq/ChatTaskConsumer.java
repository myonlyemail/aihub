package com.aihub.chat.mq;

import com.aihub.chat.dto.ChatRequest;
import com.aihub.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTaskConsumer {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "ai.chat.queue")
    public void handleChatTask(byte[] message) {
        try {
            String json = new String(message);
            ChatRequest request = objectMapper.readValue(json, ChatRequest.class);
            log.info("收到MQ聊天任务: model={}", request.getModelName());
            chatService.chatSync(request, 1L);
        } catch (Exception e) {
            log.error("MQ聊天任务处理失败", e);
        }
    }
}
