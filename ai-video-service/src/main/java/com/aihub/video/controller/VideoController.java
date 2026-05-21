package com.aihub.video.controller;

import com.aihub.common.result.Result;
import com.aihub.video.dto.VideoGenerateRequest;
import com.aihub.video.service.VideoService;
import com.aihub.video.vo.VideoGenerateVO;
import com.aihub.video.vo.VideoTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI视频生成", description = "文生视频/图生视频/多模型支持")
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @Operation(summary = "提交视频生成任务")
    @PostMapping("/generate")
    public Result<VideoGenerateVO> generate(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody VideoGenerateRequest request) {
        return Result.success(videoService.generate(request, userId));
    }

    @Operation(summary = "查询任务状态")
    @GetMapping("/task/{taskId}")
    public Result<VideoTaskVO> getTask(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long taskId) {
        return Result.success(videoService.getTask(taskId, userId));
    }

    @Operation(summary = "获取任务列表")
    @GetMapping("/tasks")
    public Result<List<VideoTaskVO>> getTasks(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.success(videoService.getTasks(userId));
    }

    @Operation(summary = "重试失败任务")
    @PostMapping("/task/{taskId}/retry")
    public Result<?> retryTask(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long taskId) {
        videoService.retryTask(taskId, userId);
        return Result.success();
    }
}
