package com.aihub.auth.service.impl;

import com.aihub.auth.service.WechatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WechatServiceImpl implements WechatService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${aihub.wechat.app-id:}")
    private String appId;

    @Value("${aihub.wechat.app-secret:}")
    private String appSecret;

    public WechatServiceImpl(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> getWechatSession(String code) {
        if (appId.isEmpty() || appSecret.isEmpty()) {
            log.warn("微信小程序未配置，使用开发模式");
            Map<String, String> devResult = new HashMap<>();
            devResult.put("openid", "dev_openid_" + code);
            devResult.put("session_key", "dev_session_key_" + code);
            devResult.put("unionid", "dev_unionid_" + code);
            return devResult;
        }

        try {
            String url = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    appId, appSecret, code);

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonNode node = objectMapper.readTree(response.body().string());
                    Map<String, String> result = new HashMap<>();
                    if (node.has("openid")) {
                        result.put("openid", node.get("openid").asText());
                    }
                    if (node.has("session_key")) {
                        result.put("session_key", node.get("session_key").asText());
                    }
                    if (node.has("unionid")) {
                        result.put("unionid", node.get("unionid").asText());
                    }
                    if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                        log.error("微信API错误: {}", node);
                        throw new RuntimeException("微信登录失败: " + node.get("errmsg").asText());
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("微信登录异常", e);
            throw new RuntimeException("微信登录失败", e);
        }
        throw new RuntimeException("微信登录失败");
    }
}
