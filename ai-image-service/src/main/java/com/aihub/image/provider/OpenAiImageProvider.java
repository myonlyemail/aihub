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
public class OpenAiImageProvider implements ImageProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.openai.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    public OpenAiImageProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "openai-image";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.equals("dall-e-3") || modelName.equals("dall-e-2");
    }

    @Override
    public ImageResult generate(ImageTask task) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", task.getModel());
            body.put("prompt", task.getPrompt());
            body.put("n", 1);
            body.put("size", task.getWidth() + "x" + task.getHeight());
            body.put("quality", "standard");
            body.put("response_format", "url");

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("DALL-E API error [{}]: {}", response.code(), err);
                    return ImageResult.builder().success(false).error("DALL-E返回错误: " + response.code()).build();
                }

                JsonNode node = objectMapper.readTree(response.body().string());
                JsonNode data = node.get("data");
                if (data != null && data.isArray() && data.size() > 0) {
                    String url = data.get(0).get("url").asText();
                    return ImageResult.builder()
                            .success(true)
                            .imageUrls(List.of(url))
                            .model(task.getModel())
                            .tokenCost(50)
                            .build();
                }
                return ImageResult.builder().success(false).error("DALL-E返回空结果").build();
            }
        } catch (IOException e) {
            log.error("DALL-E API 调用异常", e);
            return ImageResult.builder().success(false).error("DALL-E调用异常: " + e.getMessage()).build();
        }
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
