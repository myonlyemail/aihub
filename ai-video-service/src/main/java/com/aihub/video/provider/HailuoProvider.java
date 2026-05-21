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
public class HailuoProvider implements VideoProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.hailuo.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.hailuo.base-url:https://api.minimax.chat}")
    private String baseUrl;

    public HailuoProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "hailuo";
    }

    @Override
    public boolean supports(String modelName) {
        String lower = modelName.toLowerCase();
        return lower.contains("hailuo") || lower.contains("海螺") || lower.contains("minimax");
    }

    @Override
    public VideoResult generate(VideoTask task) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "video-01");
            body.put("prompt", task.getPrompt());
            body.put("duration", task.getDuration() != null ? task.getDuration() : 5);

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/video_generation")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Hailuo API error [{}]: {}", response.code(), err);
                    return VideoResult.builder().success(false).error("海螺返回错误: " + response.code()).build();
                }

                JsonNode node = objectMapper.readTree(response.body().string());
                JsonNode baseResp = node.get("base_resp");
                if (baseResp != null && baseResp.get("status_code").asInt() != 0) {
                    return VideoResult.builder().success(false)
                            .error("海螺错误: " + baseResp.get("status_msg").asText()).build();
                }

                String taskId = node.get("task_id").asText();
                String videoUrl = pollHailuoTask(taskId);

                if (videoUrl != null) {
                    return VideoResult.builder()
                            .success(true)
                            .videoUrl(videoUrl)
                            .model(task.getModel())
                            .tokenCost(200)
                            .build();
                }
                return VideoResult.builder().success(false).error("海螺任务超时").build();
            }
        } catch (IOException e) {
            log.error("Hailuo API 调用异常", e);
            return VideoResult.builder().success(false).error("海螺调用异常: " + e.getMessage()).build();
        }
    }

    private String pollHailuoTask(String taskId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(3000);
                Request req = new Request.Builder()
                        .url(baseUrl + "/v1/query/video_generation?task_id=" + taskId)
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JsonNode node = objectMapper.readTree(resp.body().string());
                        String status = node.get("status").asText();
                        if ("Success".equals(status)) {
                            return node.get("video_url").asText();
                        } else if ("Failed".equals(status)) {
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Hailuo轮询异常: taskId={}", taskId, e);
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
