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
public class RunwayProvider implements VideoProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.runway.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.runway.base-url:https://api.runwayml.com}")
    private String baseUrl;

    public RunwayProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "runway";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.toLowerCase().contains("runway") || modelName.toLowerCase().contains("gen-3");
    }

    @Override
    public VideoResult generate(VideoTask task) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("mode", "text_to_video");
            body.put("prompt_text", task.getPrompt());
            body.put("duration", task.getDuration() != null ? task.getDuration() : 5);

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/tasks")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Runway API error [{}]: {}", response.code(), err);
                    return VideoResult.builder().success(false).error("Runway返回错误: " + response.code()).build();
                }

                JsonNode node = objectMapper.readTree(response.body().string());
                String taskId = node.get("id").asText();
                String videoUrl = pollRunwayTask(taskId);

                if (videoUrl != null) {
                    return VideoResult.builder()
                            .success(true)
                            .videoUrl(videoUrl)
                            .model(task.getModel())
                            .tokenCost(200)
                            .build();
                }
                return VideoResult.builder().success(false).error("Runway任务超时").build();
            }
        } catch (IOException e) {
            log.error("Runway API 调用异常", e);
            return VideoResult.builder().success(false).error("Runway调用异常: " + e.getMessage()).build();
        }
    }

    private String pollRunwayTask(String taskId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(5000);
                Request req = new Request.Builder()
                        .url(baseUrl + "/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JsonNode node = objectMapper.readTree(resp.body().string());
                        String status = node.get("status").asText();
                        if ("SUCCEEDED".equals(status)) {
                            JsonNode output = node.get("output");
                            if (output != null && output.isArray() && output.size() > 0) {
                                return output.get(0).asText();
                            }
                        } else if ("FAILED".equals(status)) {
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Runway轮询异常: taskId={}", taskId, e);
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
