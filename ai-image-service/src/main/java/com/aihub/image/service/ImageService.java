package com.aihub.image.service;

import com.aihub.image.dto.ImageGenerateRequest;
import com.aihub.image.vo.ImageGenerateVO;
import com.aihub.image.vo.ImageTaskVO;

import java.util.List;

public interface ImageService {

    ImageGenerateVO generate(ImageGenerateRequest request, Long userId);

    ImageTaskVO getTask(Long taskId, Long userId);

    List<ImageTaskVO> getTasks(Long userId);

    void retryTask(Long taskId, Long userId);
}
