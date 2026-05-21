package com.aihub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    private Long sessionId;
    private String model;

    @NotBlank(message = "模型不能为空")
    private String modelName;

    private Boolean stream = true;
    private List<HistoryMessage> history;

    @Data
    public static class HistoryMessage {
        private String role;
        private String content;
    }
}
