package com.aihub.chat.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "aihub.providers")
public class ProviderConfig {

    private Map<String, ProviderInfo> models;

    @Data
    public static class ProviderInfo {
        private String provider;
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}
