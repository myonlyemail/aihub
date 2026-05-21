package com.aihub.chat.provider;

import com.aihub.chat.dto.ProviderRequest;
import com.aihub.chat.dto.ProviderResponse;

import java.util.function.Consumer;

public interface AiProvider {

    String getProviderName();

    boolean supports(String modelName);

    ProviderResponse chat(ProviderRequest request);

    void chatStream(ProviderRequest request, Consumer<String> onChunk, Consumer<ProviderResponse> onComplete, Consumer<Throwable> onError);
}
