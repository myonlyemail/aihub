package com.aihub.video.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VideoProviderRouter {

    private final List<VideoProvider> providers;
    private final Map<String, VideoProvider> providerMap;

    public VideoProviderRouter(List<VideoProvider> providers) {
        this.providers = providers;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(VideoProvider::getProviderName, p -> p));
    }

    public VideoProvider route(String modelName) {
        VideoProvider provider = providers.stream()
                .filter(p -> p.supports(modelName))
                .findFirst()
                .orElse(null);

        if (provider == null) {
            log.warn("未找到支持模型 [{}] 的VideoProvider, 使用Runway", modelName);
            return providerMap.get("runway");
        }

        log.info("路由视频模型 [{}] → Provider [{}]", modelName, provider.getProviderName());
        return provider;
    }
}
