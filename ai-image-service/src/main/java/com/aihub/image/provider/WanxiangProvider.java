package com.aihub.image.provider;

import com.aihub.image.dto.ImageResult;
import com.aihub.image.entity.ImageTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 通义万相 — 阿里 DashScope 图片生成 API
 */
@Slf4j
@Component
public class WanxiangProvider implements ImageProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.wanxiang.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.wanxiang.base-url:https://dashscope.aliyuncs.com}")
    private String baseUrl;

    public WanxiangProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "wanxiang";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.toLowerCase().contains("wanxiang") || modelName.toLowerCase().contains("wanx")
                || modelName.startsWith("wanx-");
    }

    @Override
    public ImageResult generate(ImageTask task) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "wanx-v1");

            Map<String, Object> input = new HashMap<>();
            input.put("prompt", task.getPrompt());
            if (task.getNegativePrompt() != null && !task.getNegativePrompt().isBlank()) {
                Map<String, Object> negPrompt = new HashMap<>();
                negPrompt.put("negative_prompt", task.getNegativePrompt());
                input.put("negative_prompt", task.getNegativePrompt());
            }
            body.put("input", input);

            Map<String, Object> params = new HashMap<>();
            int w = task.getWidth() != null ? task.getWidth() : 1024;
            int h = task.getHeight() != null ? task.getHeight() : 1024;
            params.put("size", w + "*" + h);
            params.put("n", 1);
            body.put("parameters", params);

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/api/v1/services/aigc/text2image/image-synthesis")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("X-DashScope-Async", "enable")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Wanxiang API error [{}]: {}", response.code(), err);
                    return ImageResult.builder()
                            .success(false)
                            .error("通义万相返回错误: " + response.code())
                            .build();
                }

                String bodyStr = response.body().string();
                JsonNode node = objectMapper.readTree(bodyStr);
                JsonNode output = node.get("output");
                if (output == null) {
                    return ImageResult.builder().success(false).error("通义万相返回空结果").build();
                }

                String taskId = output.get("task_id").asText();
                String taskStatus = output.get("task_status").asText();

                if ("FAILED".equals(taskStatus)) {
                    return ImageResult.builder().success(false).error("通义万相任务失败").build();
                }

                if ("SUCCEEDED".equals(taskStatus)) {
                    JsonNode results = output.get("results");
                    if (results != null && results.isArray() && results.size() > 0) {
                        return ImageResult.builder()
                                .success(true)
                                .imageUrls(List.of(results.get(0).get("url").asText()))
                                .model(task.getModel())
                                .tokenCost(50)
                                .build();
                    }
                }

                String imageUrl = pollWanxiangTask(taskId);
                if (imageUrl != null) {
                    return ImageResult.builder()
                            .success(true)
                            .imageUrls(List.of(imageUrl))
                            .model(task.getModel())
                            .tokenCost(50)
                            .build();
                }
                return ImageResult.builder().success(false).error("通义万相任务超时").build();
            }
        } catch (IOException e) {
            log.error("通义万相 API 调用异常", e);
            return ImageResult.builder().success(false).error("通义万相调用异常: " + e.getMessage()).build();
        }
    }

    private String pollWanxiangTask(String taskId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(2000);
                Request req = new Request.Builder()
                        .url(baseUrl + "/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JsonNode node = objectMapper.readTree(resp.body().string());
                        JsonNode output = node.get("output");
                        if (output == null) continue;
                        String status = output.get("task_status").asText();
                        if ("SUCCEEDED".equals(status)) {
                            JsonNode results = output.get("results");
                            if (results != null && results.isArray() && results.size() > 0) {
                                return results.get(0).get("url").asText();
                            }
                        } else if ("FAILED".equals(status)) {
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("通义万相轮询异常: taskId={}", taskId, e);
            }
        }
        return null;
    }

    @Override
    public void generateAsync(ImageTask task, Consumer<ImageResult> onComplete, Consumer<Throwable> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                onComplete.accept(generate(task));
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }
}
