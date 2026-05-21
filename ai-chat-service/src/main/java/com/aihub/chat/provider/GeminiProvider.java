package com.aihub.chat.provider;

import com.aihub.chat.dto.ProviderRequest;
import com.aihub.chat.dto.ProviderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Google Gemini — 原生 Gemini API 协议
 */
@Slf4j
@Component
public class GeminiProvider implements AiProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.gemini.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.gemini.base-url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    public GeminiProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.toLowerCase().startsWith("gemini");
    }

    @Override
    public ProviderResponse chat(ProviderRequest request) {
        try {
            String json = buildRequestBody(request);
            String model = resolveModel(request.getModel());

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.error("Gemini API error [{}]: {}", response.code(), err);
                    return ProviderResponse.builder().success(false)
                            .error("Gemini返回错误: " + response.code()).build();
                }
                String bodyStr = response.body() != null ? response.body().string() : "";
                JsonNode node = objectMapper.readTree(bodyStr);
                JsonNode candidates = node.get("candidates");
                if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                    JsonNode content = candidates.get(0).get("content");
                    JsonNode parts = content.get("parts");
                    String text = "";
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        text = parts.get(0).get("text").asText("");
                    }
                    int promptTokens = node.get("usageMetadata").get("promptTokenCount").asInt();
                    int completionTokens = node.get("usageMetadata").get("candidatesTokenCount").asInt();
                    return ProviderResponse.builder()
                            .success(true).content(text)
                            .promptTokens(promptTokens).completionTokens(completionTokens)
                            .model(request.getModel()).build();
                }
                return ProviderResponse.builder().success(false).error("Gemini返回空结果").build();
            }
        } catch (IOException e) {
            log.error("Gemini API 调用异常", e);
            return ProviderResponse.builder().success(false).error("Gemini调用异常: " + e.getMessage()).build();
        }
    }

    @Override
    public void chatStream(ProviderRequest request,
                           Consumer<String> onChunk,
                           Consumer<ProviderResponse> onComplete,
                           Consumer<Throwable> onError) {
        try {
            String json = buildRequestBody(request);
            String model = resolveModel(request.getModel());

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            httpClient.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    onError.accept(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        onError.accept(new RuntimeException("Gemini返回错误: " + response.code()));
                        return;
                    }
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                        StringBuilder full = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode candidates = node.get("candidates");
                                    if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                                        JsonNode parts = candidates.get(0).get("content").get("parts");
                                        if (parts != null && parts.isArray() && parts.size() > 0) {
                                            String chunk = parts.get(0).get("text").asText("");
                                            full.append(chunk);
                                            onChunk.accept(chunk);
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                        onComplete.accept(ProviderResponse.builder()
                                .success(true).content(full.toString()).model(request.getModel()).build());
                    }
                }
            });
        } catch (IOException e) {
            onError.accept(e);
        }
    }

    private String buildRequestBody(ProviderRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");

        for (ProviderRequest.Message msg : request.getMessages()) {
            ObjectNode content = contents.addObject();
            content.put("role", "user".equals(msg.getRole()) ? "user" : "model");
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", msg.getContent());
        }

        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.7);
        genConfig.put("maxOutputTokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);

        return objectMapper.writeValueAsString(root);
    }

    private String resolveModel(String model) {
        return model.replace("gemini/", "").replace("gemini-", "gemini-");
    }
}
