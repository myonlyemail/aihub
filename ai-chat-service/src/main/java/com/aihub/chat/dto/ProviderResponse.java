package com.aihub.chat.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderResponse {

    private String content;
    private Integer promptTokens;
    private Integer completionTokens;
    private String model;
    private boolean success;
    private String error;
}
