package com.aihub.video.provider;

import com.aihub.video.dto.VideoResult;
import com.aihub.video.entity.VideoTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Component
public class KlingProvider implements VideoProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.kling.access-key:}")
    private String accessKey;

    @Value("${aihub.provider.kling.secret-key:}")
    private String secretKey;

    @Value("${aihub.provider.kling.base-url:https://api.klingai.com}")
    private String baseUrl;

    public KlingProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "kling";
    }

    @Override
    public boolean supports(String modelName) {
        String lower = modelName.toLowerCase();
        return lower.contains("kling") || lower.contains("可灵");
    }

    @Override
    public VideoResult generate(VideoTask task) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model_name", "kling-v1");
            body.put("prompt", task.getPrompt());
            body.put("duration", task.getDuration() != null ? task.getDuration() : 5);
            body.put("mode", "std");

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/videos/text2video")
                    .header("Authorization", "Bearer " + accessKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Kling API error [{}]: {}", response.code(), err);
                    return VideoResult.builder().success(false).error("可灵返回错误: " + response.code()).build();
                }

                JsonNode node = objectMapper.readTree(response.body().string());
                if (node.get("code").asInt() != 0) {
                    return VideoResult.builder().success(false)
                            .error("可灵错误: " + node.get("message").asText()).build();
                }

                String taskId = node.get("data").get("task_id").asText();
                String videoUrl = pollKlingTask(taskId);

                if (videoUrl != null) {
                    return VideoResult.builder()
                            .success(true)
                            .videoUrl(videoUrl)
                            .model(task.getModel())
                            .tokenCost(200)
                            .build();
                }
                return VideoResult.builder().success(false).error("可灵任务超时").build();
            }
        } catch (IOException e) {
            log.error("Kling API 调用异常", e);
            return VideoResult.builder().success(false).error("可灵调用异常: " + e.getMessage()).build();
        }
    }

    private String pollKlingTask(String taskId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(3000);
                Request req = new Request.Builder()
                        .url(baseUrl + "/v1/videos/text2video/" + taskId)
                        .header("Authorization", "Bearer " + accessKey)
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JsonNode node = objectMapper.readTree(resp.body().string());
                        if (node.get("code").asInt() == 0) {
                            JsonNode data = node.get("data");
                            String status = data.get("task_status").asText();
                            if ("succeed".equals(status)) {
                                JsonNode videos = data.get("task_result").get("videos");
                                if (videos != null && videos.isArray() && videos.size() > 0) {
                                    return videos.get(0).get("url").asText();
                                }
                            } else if ("failed".equals(status)) {
                                return null;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Kling轮询异常: taskId={}", taskId, e);
            }
        }
        return null;
    }

    @Override
    public void generateAsync(VideoTask task, Consumer<VideoResult> onComplete, Consumer<Throwable> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                onComplete.accept(generate(task));
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }
}
