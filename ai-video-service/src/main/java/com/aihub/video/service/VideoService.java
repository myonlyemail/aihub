package com.aihub.video.service;

import com.aihub.video.dto.VideoGenerateRequest;
import com.aihub.video.vo.VideoGenerateVO;
import com.aihub.video.vo.VideoTaskVO;

import java.util.List;

public interface VideoService {

    VideoGenerateVO generate(VideoGenerateRequest request, Long userId);

    VideoTaskVO getTask(Long taskId, Long userId);

    List<VideoTaskVO> getTasks(Long userId);

    void retryTask(Long taskId, Long userId);
}
