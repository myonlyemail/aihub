package com.aihub.chat.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProviderRouter {

    private final Map<String, AiProvider> providerMap;
    private final List<AiProvider> fallbackProviders;

    public ProviderRouter(List<AiProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(AiProvider::getProviderName, p -> p));
        this.fallbackProviders = providers;
    }

    public AiProvider route(String modelName) {
        AiProvider provider = fallbackProviders.stream()
                .filter(p -> p.supports(modelName))
                .findFirst()
                .orElse(null);

        if (provider == null) {
            log.warn("未找到支持模型 [{}] 的Provider, 使用默认OpenAI兼容Provider", modelName);
            return providerMap.get("openai");
        }

        log.info("路由模型 [{}] → Provider [{}]", modelName, provider.getProviderName());
        return provider;
    }

    public AiProvider getProvider(String providerName) {
        return providerMap.get(providerName);
    }

    public List<String> getAvailableModels() {
        return List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo",
                "claude-3-opus", "claude-3-sonnet", "claude-3-haiku",
                "deepseek-chat", "deepseek-reasoner",
                "qwen-turbo", "qwen-plus", "qwen-max",
                "glm-4", "glm-4-flash",
                "moonshot-v1-8k", "moonshot-v1-32k",
                "doubao-lite-32k", "doubao-pro-128k",
                "gemini-2.0-flash", "gemini-1.5-pro",
                "ernie-4.0-8k", "ernie-3.5-8k", "ernie-speed-8k");
    }
}
