package com.aihub.chat.provider;

import cn.hutool.core.io.IoUtil;
import com.aihub.chat.dto.ProviderRequest;
import com.aihub.chat.dto.ProviderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class OpenAiProvider implements AiProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.openai.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    public OpenAiProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public boolean supports(String modelName) {
        return modelName.startsWith("gpt-") || modelName.startsWith("o1") || modelName.startsWith("o3");
    }

    @Override
    public ProviderResponse chat(ProviderRequest request) {
        try {
            Map<String, Object> body = buildRequestBody(request, false);
            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown error";
                    log.error("OpenAI API error [{}]: {}", response.code(), errorBody);
                    return ProviderResponse.builder()
                            .success(false)
                            .error("OpenAI返回错误: " + response.code())
                            .build();
                }

                String bodyStr = response.body() != null ? response.body().string() : "";
                JsonNode node = objectMapper.readTree(bodyStr);
                JsonNode choices = node.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).get("message").get("content").asText();
                    int promptTokens = node.get("usage").get("prompt_tokens").asInt();
                    int completionTokens = node.get("usage").get("completion_tokens").asInt();

                    return ProviderResponse.builder()
                            .success(true)
                            .content(content)
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .model(request.getModel())
                            .build();
                }

                return ProviderResponse.builder().success(false).error("OpenAI返回空结果").build();
            }
        } catch (IOException e) {
            log.error("OpenAI API 调用异常", e);
            return ProviderResponse.builder().success(false).error("OpenAI调用异常: " + e.getMessage()).build();
        }
    }

    @Override
    public void chatStream(ProviderRequest request,
                           Consumer<String> onChunk,
                           Consumer<ProviderResponse> onComplete,
                           Consumer<Throwable> onError) {
        try {
            Map<String, Object> body = buildRequestBody(request, true);
            String json = objectMapper.writeValueAsString(body);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            httpClient.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("OpenAI stream 调用失败", e);
                    onError.accept(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        onError.accept(new RuntimeException("OpenAI返回错误: " + response.code()));
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                        StringBuilder fullContent = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode choices = node.get("choices");
                                    if (choices != null && choices.isArray() && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null && delta.has("content")) {
                                            String chunk = delta.get("content").asText();
                                            fullContent.append(chunk);
                                            onChunk.accept(chunk);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("解析SSE数据异常: {}", data, e);
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
            log.error("OpenAI stream 请求构建失败", e);
            onError.accept(e);
        }
    }

    private Map<String, Object> buildRequestBody(ProviderRequest request, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful assistant."));
        for (ProviderRequest.Message msg : request.getMessages()) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", request.getModel());
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        body.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
        return body;
    }
}
