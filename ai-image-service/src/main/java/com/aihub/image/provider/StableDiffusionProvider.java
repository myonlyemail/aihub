package com.aihub.image.provider;

import cn.hutool.core.io.IoUtil;
import com.aihub.image.dto.ImageResult;
import com.aihub.image.entity.ImageTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Component
public class StableDiffusionProvider implements ImageProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.sd.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.sd.base-url:}")
    private String baseUrl;

    public StableDiffusionProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "stable-diffusion";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.contains("stable-diffusion") || modelName.contains("sd")
                || modelName.equals("sdxl") || modelName.equals("sd3");
    }

    @Override
    public ImageResult generate(ImageTask task) {
        // 使用 Stability AI API: POST /v1/generation/{engine_id}/text-to-image
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("text_prompts", List.of(
                    Map.of("text", task.getPrompt(), "weight", 1.0)
            ));
            if (task.getNegativePrompt() != null && !task.getNegativePrompt().isBlank()) {
                body.put("text_prompts", List.of(
                        Map.of("text", task.getPrompt(), "weight", 1.0),
                        Map.of("text", task.getNegativePrompt(), "weight", -1.0)
                ));
            }
            body.put("width", task.getWidth() != null ? task.getWidth() : 1024);
            body.put("height", task.getHeight() != null ? task.getHeight() : 1024);
            body.put("samples", 1);
            body.put("cfg_scale", 7);
            body.put("steps", 30);
            body.put("style_preset", "photographic");

            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("SD API error [{}]: {}", response.code(), errorBody);
                    return ImageResult.builder()
                            .success(false)
                            .error("Stable Diffusion返回错误: " + response.code())
                            .build();
                }

                String bodyStr = response.body().string();
                JsonNode node = objectMapper.readTree(bodyStr);
                JsonNode artifacts = node.get("artifacts");
                if (artifacts != null && artifacts.isArray() && artifacts.size() > 0) {
                    String base64Image = artifacts.get(0).get("base64").asText();
                    byte[] imageBytes = Base64.getDecoder().decode(base64Image);

                    return ImageResult.builder()
                            .success(true)
                            .imageUrls(List.of("data:image/png;base64," + base64Image))
                            .model(task.getModel())
                            .tokenCost(50)
                            .build();
                }

                return ImageResult.builder().success(false).error("SD返回空结果").build();
            }
        } catch (IOException e) {
            log.error("SD API 调用异常", e);
            return ImageResult.builder().success(false).error("SD调用异常: " + e.getMessage()).build();
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
}
