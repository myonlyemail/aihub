package com.aihub.auth.service.impl;

import com.aihub.auth.service.OAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class OAuthServiceImpl implements OAuthService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.oauth.google.client-id:}")
    private String googleClientId;

    @Value("${aihub.oauth.google.client-secret:}")
    private String googleClientSecret;

    @Value("${aihub.oauth.github.client-id:}")
    private String githubClientId;

    @Value("${aihub.oauth.github.client-secret:}")
    private String githubClientSecret;

    public OAuthServiceImpl(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> getOAuthUserInfo(String provider, String code, String redirectUri) {
        return switch (provider.toLowerCase()) {
            case "google" -> getGoogleUserInfo(code, redirectUri);
            case "github" -> getGithubUserInfo(code, redirectUri);
            default -> throw new IllegalArgumentException("不支持的OAuth提供商: " + provider);
        };
    }

    private Map<String, Object> getGoogleUserInfo(String code, String redirectUri) {
        if (googleClientId.isEmpty()) {
            log.warn("Google OAuth 未配置，使用开发模式");
            return mockUserInfo("google", code);
        }

        try {
            // Exchange code for access token
            FormBody body = new FormBody.Builder()
                    .add("client_id", googleClientId)
                    .add("client_secret", googleClientSecret)
                    .add("code", code)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", redirectUri != null ? redirectUri : "")
                    .build();

            Request tokenRequest = new Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(body)
                    .build();

            String accessToken;
            try (Response resp = httpClient.newCall(tokenRequest).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("Google token交换失败");
                }
                JsonNode node = objectMapper.readTree(resp.body().string());
                accessToken = node.get("access_token").asText();
            }

            // Get user info
            Request userRequest = new Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v2/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            try (Response resp = httpClient.newCall(userRequest).execute()) {
                JsonNode node = objectMapper.readTree(resp.body().string());
                Map<String, Object> result = new HashMap<>();
                result.put("openid", node.get("id").asText());
                result.put("nickname", node.get("name").asText());
                result.put("avatar", node.get("picture").asText());
                result.put("email", node.get("email").asText());
                return result;
            }
        } catch (Exception e) {
            log.error("Google OAuth登录异常", e);
            throw new RuntimeException("Google登录失败", e);
        }
    }

    private Map<String, Object> getGithubUserInfo(String code, String redirectUri) {
        if (githubClientId.isEmpty()) {
            log.warn("GitHub OAuth 未配置，使用开发模式");
            return mockUserInfo("github", code);
        }

        try {
            // Exchange code for access token
            FormBody body = new FormBody.Builder()
                    .add("client_id", githubClientId)
                    .add("client_secret", githubClientSecret)
                    .add("code", code)
                    .add("redirect_uri", redirectUri != null ? redirectUri : "")
                    .build();

            Request tokenRequest = new Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(body)
                    .build();

            String accessToken;
            try (Response resp = httpClient.newCall(tokenRequest).execute()) {
                JsonNode node = objectMapper.readTree(resp.body().string());
                accessToken = node.get("access_token").asText();
            }

            // Get user info
            Request userRequest = new Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .get()
                    .build();

            try (Response resp = httpClient.newCall(userRequest).execute()) {
                JsonNode node = objectMapper.readTree(resp.body().string());
                Map<String, Object> result = new HashMap<>();
                result.put("openid", String.valueOf(node.get("id").asLong()));
                result.put("nickname", node.get("login").asText());
                result.put("avatar", node.get("avatar_url").asText());
                result.put("email", node.has("email") && !node.get("email").isNull()
                        ? node.get("email").asText() : "");
                return result;
            }
        } catch (Exception e) {
            log.error("GitHub OAuth登录异常", e);
            throw new RuntimeException("GitHub登录失败", e);
        }
    }

    private Map<String, Object> mockUserInfo(String provider, String code) {
        Map<String, Object> result = new HashMap<>();
        result.put("openid", provider + "_openid_" + code);
        result.put("nickname", provider + "_user_" + code.substring(0, Math.min(code.length(), 8)));
        result.put("avatar", "");
        result.put("email", provider + "_" + code.substring(0, Math.min(code.length(), 6)) + "@example.com");
        return result;
    }
}
