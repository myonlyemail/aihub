package com.aihub.image.controller;

import com.aihub.image.dto.ImageGenerateRequest;
import com.aihub.image.service.ImageService;
import com.aihub.image.vo.ImageGenerateVO;
import com.aihub.image.vo.ImageTaskVO;
import com.aihub.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI图片生成", description = "文生图/图生图/多模型支持")
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "提交图片生成任务")
    @PostMapping("/generate")
    public Result<ImageGenerateVO> generate(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ImageGenerateRequest request) {
        return Result.success(imageService.generate(request, userId));
    }

    @Operation(summary = "查询任务状态")
    @GetMapping("/task/{taskId}")
    public Result<ImageTaskVO> getTask(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long taskId) {
        return Result.success(imageService.getTask(taskId, userId));
    }

    @Operation(summary = "获取任务列表")
    @GetMapping("/tasks")
    public Result<List<ImageTaskVO>> getTasks(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.success(imageService.getTasks(userId));
    }

    @Operation(summary = "重试失败任务")
    @PostMapping("/task/{taskId}/retry")
    public Result<?> retryTask(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long taskId) {
        imageService.retryTask(taskId, userId);
        return Result.success();
    }
}
