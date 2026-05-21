package com.aihub.chat.provider;

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

/**
 * 文心一言 — 百度千帆 API (需 OAuth access_token)
 */
@Slf4j
@Component
public class WenxinProvider implements AiProvider {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.provider.wenxin.api-key:}")
    private String apiKey;

    @Value("${aihub.provider.wenxin.secret-key:}")
    private String secretKey;

    @Value("${aihub.provider.wenxin.base-url:https://aip.baidubce.com}")
    private String baseUrl;

    private volatile String cachedAccessToken;
    private volatile long tokenExpireTime;

    public WenxinProvider(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "wenxin";
    }

    @Override
    public boolean supports(String modelName) {
        String name = modelName.toLowerCase();
        return name.startsWith("ernie") || name.contains("wenxin");
    }

    @Override
    public ProviderResponse chat(ProviderRequest request) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return ProviderResponse.builder().success(false).error("文心一言获取access_token失败").build();
            }

            String model = resolveModel(request.getModel());
            String json = buildRequestBody(request, false);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/" + model + "?access_token=" + accessToken)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.code() == 401 || response.code() == 403) {
                    invalidateToken();
                    return ProviderResponse.builder().success(false).error("文心一言认证失败，请检查API Key").build();
                }
                if (!response.isSuccessful()) {
                    return ProviderResponse.builder().success(false)
                            .error("文心一言返回错误: " + response.code()).build();
                }
                String bodyStr = response.body() != null ? response.body().string() : "";
                JsonNode node = objectMapper.readTree(bodyStr);
                String result = node.get("result").asText(null);
                if (result != null) {
                    int promptTokens = node.get("usage").get("prompt_tokens").asInt();
                    int completionTokens = node.get("usage").get("completion_tokens").asInt();
                    return ProviderResponse.builder()
                            .success(true).content(result)
                            .promptTokens(promptTokens).completionTokens(completionTokens)
                            .model(request.getModel()).build();
                }
                String errorMsg = node.has("error_msg") ? node.get("error_msg").asText() : "未知错误";
                return ProviderResponse.builder().success(false).error("文心一言: " + errorMsg).build();
            }
        } catch (IOException e) {
            log.error("文心一言 API 调用异常", e);
            return ProviderResponse.builder().success(false).error("文心一言调用异常: " + e.getMessage()).build();
        }
    }

    @Override
    public void chatStream(ProviderRequest request,
                           Consumer<String> onChunk,
                           Consumer<ProviderResponse> onComplete,
                           Consumer<Throwable> onError) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                onError.accept(new RuntimeException("文心一言获取access_token失败"));
                return;
            }

            String model = resolveModel(request.getModel());
            String json = buildRequestBody(request, true);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/" + model + "?access_token=" + accessToken)
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
                        onError.accept(new RuntimeException("文心一言返回错误: " + response.code()));
                        return;
                    }
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                        StringBuilder full = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) break;
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    String result = node.has("result") ? node.get("result").asText() : null;
                                    if (result != null) {
                                        full.append(result);
                                        onChunk.accept(result);
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

    private synchronized String getAccessToken() {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedAccessToken;
        }
        try {
            Request tokenRequest = new Request.Builder()
                    .url(baseUrl + "/oauth/2.0/token?grant_type=client_credentials"
                            + "&client_id=" + apiKey
                            + "&client_secret=" + secretKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .build();
            try (Response resp = httpClient.newCall(tokenRequest).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    JsonNode node = objectMapper.readTree(resp.body().string());
                    cachedAccessToken = node.get("access_token").asText();
                    int expiresIn = node.get("expires_in").asInt(2592000);
                    tokenExpireTime = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
                    log.info("文心一言 access_token 刷新成功");
                    return cachedAccessToken;
                }
                log.error("获取文心一言 access_token 失败: {}", resp.code());
            }
        } catch (IOException e) {
            log.error("获取文心一言 access_token 异常", e);
        }
        return null;
    }

    private void invalidateToken() {
        cachedAccessToken = null;
        tokenExpireTime = 0;
    }

    private String buildRequestBody(ProviderRequest request, boolean stream) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ProviderRequest.Message msg : request.getMessages()) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("max_output_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        return objectMapper.writeValueAsString(body);
    }

    private String resolveModel(String model) {
        return switch (model.toLowerCase()) {
            case "ernie-4.0", "ernie-4.0-8k" -> "completions_pro";
            case "ernie-3.5", "ernie-3.5-8k" -> "completions";
            case "ernie-speed", "ernie-speed-8k" -> "ernie_speed";
            default -> model;
        };
    }
}
