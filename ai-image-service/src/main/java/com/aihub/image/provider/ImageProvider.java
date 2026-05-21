package com.aihub.image.provider;

import com.aihub.image.dto.ImageResult;
import com.aihub.image.entity.ImageTask;

import java.util.function.Consumer;

public interface ImageProvider {

    String getProviderName();

    boolean supports(String modelName);

    ImageResult generate(ImageTask task);

    void generateAsync(ImageTask task, Consumer<ImageResult> onComplete, Consumer<Throwable> onError);
}
