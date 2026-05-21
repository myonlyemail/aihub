package com.aihub.chat.provider;

import cn.hutool.core.io.IoUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class ClaudeProvider implements AiProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.claude.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.claude.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${aihub.provider.claude.version:2023-06-01}")
    private String apiVersion;

    public ClaudeProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.startsWith("claude-");
    }

    @Override
    public ProviderResponse chat(ProviderRequest request) {
        try {
            String json = buildRequestBody(request, false);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", apiVersion)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown error";
                    log.error("Claude API error [{}]: {}", response.code(), errorBody);
                    return ProviderResponse.builder()
                            .success(false)
                            .error("Claude返回错误: " + response.code())
                            .build();
                }

                String bodyStr = response.body() != null ? response.body().string() : "";
                JsonNode node = objectMapper.readTree(bodyStr);

                JsonNode content = node.get("content");
                if (content != null && content.isArray() && content.size() > 0) {
                    String text = content.get(0).get("text").asText();
                    int inputTokens = node.get("usage").get("input_tokens").asInt();
                    int outputTokens = node.get("usage").get("output_tokens").asInt();

                    return ProviderResponse.builder()
                            .success(true)
                            .content(text)
                            .promptTokens(inputTokens)
                            .completionTokens(outputTokens)
                            .model(request.getModel())
                            .build();
                }

                return ProviderResponse.builder().success(false).error("Claude返回空结果").build();
            }
        } catch (IOException e) {
            log.error("Claude API 调用异常", e);
            return ProviderResponse.builder().success(false).error("Claude调用异常: " + e.getMessage()).build();
        }
    }

    @Override
    public void chatStream(ProviderRequest request,
                           Consumer<String> onChunk,
                           Consumer<ProviderResponse> onComplete,
                           Consumer<Throwable> onError) {
        try {
            String json = buildRequestBody(request, true);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", apiVersion)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            httpClient.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("Claude stream 调用失败", e);
                    onError.accept(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        onError.accept(new RuntimeException("Claude返回错误: " + response.code()));
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                        StringBuilder fullContent = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    String type = node.has("type") ? node.get("type").asText() : "";

                                    if ("content_block_delta".equals(type)) {
                                        JsonNode delta = node.get("delta");
                                        if (delta != null && delta.has("text")) {
                                            String chunk = delta.get("text").asText();
                                            fullContent.append(chunk);
                                            onChunk.accept(chunk);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("解析Claude SSE异常: {}", data, e);
                                }
                            }
                        }

                        ProviderResponse result = ProviderResponse.builder()
                                .success(true)
                                .content(fullContent.toString())
                                .promptTokens(0)
                                .completionTokens(0)
                                .model(request.getModel())
                                .build();
                        onComplete.accept(result);
                    }
                }
            });
        } catch (IOException e) {
            log.error("Claude stream 请求构建失败", e);
            onError.accept(e);
        }
    }

    private String buildRequestBody(ProviderRequest request, boolean stream) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.getModel());
        root.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        root.put("stream", stream);

        ArrayNode messages = root.putArray("messages");
        for (ProviderRequest.Message msg : request.getMessages()) {
            ObjectNode message = messages.addObject();
            message.put("role", msg.getRole());
            ArrayNode content = message.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", msg.getContent());
        }

        return objectMapper.writeValueAsString(root);
    }
}
