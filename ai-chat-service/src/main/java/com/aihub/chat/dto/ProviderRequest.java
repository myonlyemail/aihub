package com.aihub.chat.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProviderRequest {

    private String model;
    private List<Message> messages;
    private Boolean stream;
    private Integer maxTokens;
    private Double temperature;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
