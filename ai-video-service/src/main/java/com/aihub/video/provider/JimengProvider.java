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

/**
 * 即梦 (字节跳动) — 火山引擎 Ark 视频生成 API
 */
@Slf4j
@Component
public class JimengProvider implements VideoProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.jimeng.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.jimeng.base-url:https://ark.cn-beijing.volces.com}")
    private String baseUrl;

    public JimengProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "jimeng";
    }

    @Override
    public boolean supports(String modelName) {
        String lower = modelName.toLowerCase();
        return lower.contains("jimeng") || lower.contains("即梦") || lower.contains("jimeng-");
    }

    @Override
    public VideoResult generate(VideoTask task) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "jimeng-v1");
            body.put("prompt", task.getPrompt());
            body.put("duration", task.getDuration() != null ? task.getDuration() : 5);
            body.put("size", "1024x1024");

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/api/v3/video/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Jimeng API error [{}]: {}", response.code(), err);
                    return VideoResult.builder().success(false).error("即梦返回错误: " + response.code()).build();
                }

                JsonNode node = objectMapper.readTree(response.body().string());
                String taskId = node.get("id").asText();
                String videoUrl = pollJimengTask(taskId);

                if (videoUrl != null) {
                    return VideoResult.builder()
                            .success(true)
                            .videoUrl(videoUrl)
                            .model(task.getModel())
                            .tokenCost(200)
                            .build();
                }
                return VideoResult.builder().success(false).error("即梦任务超时").build();
            }
        } catch (IOException e) {
            log.error("即梦 API 调用异常", e);
            return VideoResult.builder().success(false).error("即梦调用异常: " + e.getMessage()).build();
        }
    }

    private String pollJimengTask(String taskId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(3000);
                Request req = new Request.Builder()
                        .url(baseUrl + "/api/v3/video/generations/" + taskId)
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JsonNode node = objectMapper.readTree(resp.body().string());
                        String status = node.get("status").asText();
                        if ("succeeded".equals(status)) {
                            JsonNode output = node.get("output");
                            if (output != null) {
                                String url = output.get("video_url").asText(null);
                                if (url != null) return url;
                            }
                        } else if ("failed".equals(status) || "canceled".equals(status)) {
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("即梦轮询异常: taskId={}", taskId, e);
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
