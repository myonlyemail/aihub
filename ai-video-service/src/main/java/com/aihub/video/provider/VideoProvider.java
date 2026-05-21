package com.aihub.video.provider;

import com.aihub.video.dto.VideoResult;
import com.aihub.video.entity.VideoTask;

import java.util.function.Consumer;

public interface VideoProvider {

    String getProviderName();

    boolean supports(String modelName);

    VideoResult generate(VideoTask task);

    void generateAsync(VideoTask task, Consumer<VideoResult> onComplete, Consumer<Throwable> onError);
}
