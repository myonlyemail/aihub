package com.aihub.image.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ImageProviderRouter {

    private final List<ImageProvider> providers;
    private final Map<String, ImageProvider> providerMap;

    public ImageProviderRouter(List<ImageProvider> providers) {
        this.providers = providers;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(ImageProvider::getProviderName, p -> p));
    }

    public ImageProvider route(String modelName) {
        ImageProvider provider = providers.stream()
                .filter(p -> p.supports(modelName))
                .findFirst()
                .orElse(null);

        if (provider == null) {
            log.warn("未找到支持模型 [{}] 的ImageProvider, 使用SD", modelName);
            return providerMap.get("stable-diffusion");
        }

        log.info("路由图片模型 [{}] → Provider [{}]", modelName, provider.getProviderName());
        return provider;
    }
}
