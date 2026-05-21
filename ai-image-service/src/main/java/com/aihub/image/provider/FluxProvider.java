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

@Slf4j
@Component
public class FluxProvider implements ImageProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.flux.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.flux.base-url:https://api.bfl.ml}")
    private String baseUrl;

    public FluxProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "flux";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.toLowerCase().contains("flux");
    }

    @Override
    public ImageResult generate(ImageTask task) {
        // BFL API: POST /v1/flux-pro-1.1
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("prompt", task.getPrompt());
            body.put("width", task.getWidth() != null ? task.getWidth() : 1024);
            body.put("height", task.getHeight() != null ? task.getHeight() : 768);
            body.put("num_outputs", 1);
            body.put("output_format", "jpeg");

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/flux-pro-1.1")
                    .header("x-key", apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("Flux API error [{}]: {}", response.code(), errorBody);
                    return ImageResult.builder()
                            .success(false)
                            .error("Flux返回错误: " + response.code())
                            .build();
                }

                String bodyStr = response.body().string();
                JsonNode node = objectMapper.readTree(bodyStr);

                if (node.has("status") && "Ready".equals(node.get("status").asText())) {
                    JsonNode samples = node.get("result").get("sample");
                    String imageUrl = samples.asText();

                    return ImageResult.builder()
                            .success(true)
                            .imageUrls(List.of(imageUrl))
                            .model(task.getModel())
                            .tokenCost(50)
                            .build();
                }

                if (node.has("id")) {
                    String pollUrl = node.get("polling_url").asText();
                    String resultUrl = pollForResult(pollUrl);
                    if (resultUrl != null) {
                        return ImageResult.builder()
                                .success(true)
                                .imageUrls(List.of(resultUrl))
                                .model(task.getModel())
                                .tokenCost(50)
                                .build();
                    }
                }

                return ImageResult.builder().success(false).error("Flux任务未完成").build();
            }
        } catch (IOException e) {
            log.error("Flux API 调用异常", e);
            return ImageResult.builder().success(false).error("Flux调用异常: " + e.getMessage()).build();
        }
    }

    @Override
    public void generateAsync(ImageTask task, Consumer<ImageResult> onComplete, Consumer<Throwable> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                ImageResult result = generate(task);
                onComplete.accept(result);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    private String pollForResult(String pollUrl) {
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(2000);
                Request req = new Request.Builder().url(pollUrl).get().build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JsonNode node = objectMapper.readTree(resp.body().string());
                        if ("Ready".equals(node.get("status").asText())) {
                            return node.get("result").get("sample").asText();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Flux轮询异常", e);
            }
        }
        return null;
    }
}
