package com.aihub.video.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.aihub.common.constant.RedisKey;
import com.aihub.common.exception.BusinessException;
import com.aihub.common.util.MinioUtil;
import com.aihub.video.dto.VideoGenerateRequest;
import com.aihub.video.dto.VideoResult;
import com.aihub.video.entity.VideoTask;
import com.aihub.video.mapper.VideoTaskMapper;
import com.aihub.video.mq.VideoMessageProducer;
import com.aihub.video.provider.VideoProvider;
import com.aihub.video.provider.VideoProviderRouter;
import com.aihub.video.service.VideoService;
import com.aihub.video.vo.VideoGenerateVO;
import com.aihub.video.vo.VideoTaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoProviderRouter providerRouter;
    private final VideoTaskMapper videoTaskMapper;
    private final VideoMessageProducer messageProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MinioUtil minioUtil;

    private static final int TOKEN_COST_VIDEO = 200;

    @Override
    @Transactional
    public VideoGenerateVO generate(VideoGenerateRequest request, Long userId) {
        String key = RedisKey.tokenBalance(userId);
        Long balance = redisTemplate.opsForValue().decrement(key, TOKEN_COST_VIDEO);
        if (balance != null && balance < 0) {
            redisTemplate.opsForValue().increment(key, TOKEN_COST_VIDEO);
            throw BusinessException.badRequest("Token余额不足，生成视频需要" + TOKEN_COST_VIDEO + " Token");
        }

        VideoTask task = new VideoTask();
        task.setUserId(userId);
        task.setTitle(request.getTitle() != null ? request.getTitle() : request.getPrompt());
        task.setPrompt(request.getPrompt());
        task.setModel(request.getModel());
        task.setDuration(request.getDuration());
        task.setStatus(0);
        task.setTokenCost(TOKEN_COST_VIDEO);
        videoTaskMapper.insert(task);

        messageProducer.sendVideoTask(task);

        VideoGenerateVO vo = new VideoGenerateVO();
        vo.setTaskId(task.getId());
        vo.setStatus("WAITING");
        vo.setTokenCost(TOKEN_COST_VIDEO);
        vo.setTokenBalance(balance != null ? balance : 0);
        return vo;
    }

    @Override
    public VideoTaskVO getTask(Long taskId, Long userId) {
        VideoTask task = videoTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw BusinessException.notFound();
        }
        return toVO(task);
    }

    @Override
    public List<VideoTaskVO> getTasks(Long userId) {
        List<VideoTask> tasks = videoTaskMapper.selectList(
                new LambdaQueryWrapper<VideoTask>()
                        .eq(VideoTask::getUserId, userId)
                        .orderByDesc(VideoTask::getCreateTime));
        return tasks.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void retryTask(Long taskId, Long userId) {
        VideoTask task = videoTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw BusinessException.notFound();
        }
        if (task.getStatus() != 3) {
            throw BusinessException.badRequest("只有失败的任务可以重试");
        }
        task.setStatus(0);
        videoTaskMapper.updateById(task);
        messageProducer.sendVideoTask(task);
    }

    @Transactional
    public void processVideoTask(Long taskId) {
        VideoTask task = videoTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 0) {
            return;
        }

        task.setStatus(1);
        videoTaskMapper.updateById(task);

        try {
            VideoProvider provider = providerRouter.route(task.getModel());
            VideoResult result = provider.generate(task);

            if (result.isSuccess() && result.getVideoUrl() != null) {
                task.setResultUrl(downloadAndUploadToMinio(result.getVideoUrl(), task));
                task.setStatus(2);
            } else {
                task.setStatus(3);
                refundToken(task.getUserId(), TOKEN_COST_VIDEO);
                log.error("视频生成失败: taskId={}, error={}", taskId, result.getError());
            }
        } catch (Exception e) {
            log.error("视频生成异常: taskId={}", taskId, e);
            task.setStatus(3);
            refundToken(task.getUserId(), TOKEN_COST_VIDEO);
        }

        videoTaskMapper.updateById(task);
    }

    private String downloadAndUploadToMinio(String videoUrl, VideoTask task) {
        try {
            URL url = new URL(videoUrl);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(300000);
            try (InputStream is = conn.getInputStream()) {
                byte[] bytes = IoUtil.readBytes(is);
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                return minioUtil.uploadVideo(bis, bytes.length, "generated.mp4");
            }
        } catch (Exception e) {
            log.error("下载/上传视频失败: {}", videoUrl, e);
            return videoUrl;
        }
    }

    private void refundToken(Long userId, int amount) {
        redisTemplate.opsForValue().increment(RedisKey.tokenBalance(userId), amount);
    }

    private VideoTaskVO toVO(VideoTask task) {
        VideoTaskVO vo = new VideoTaskVO();
        vo.setTaskId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setPrompt(StrUtil.sub(task.getPrompt(), 0, 100));
        vo.setModel(task.getModel());
        vo.setDuration(task.getDuration());
        vo.setStatus(task.getStatus());
        vo.setStatusDesc(getStatusDesc(task.getStatus()));
        vo.setResultUrl(task.getResultUrl());
        vo.setTokenCost(task.getTokenCost());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    private String getStatusDesc(Integer status) {
        return switch (status) {
            case 0 -> "等待中";
            case 1 -> "生成中";
            case 2 -> "已完成";
            case 3 -> "失败";
            default -> "未知";
        };
    }
}
