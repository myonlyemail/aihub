package com.aihub.image.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.aihub.common.constant.RedisKey;
import com.aihub.common.exception.BusinessException;
import com.aihub.common.util.MinioUtil;
import com.aihub.image.dto.ImageGenerateRequest;
import com.aihub.image.dto.ImageResult;
import com.aihub.image.entity.ImageTask;
import com.aihub.image.mapper.ImageTaskMapper;
import com.aihub.image.mq.ImageMessageProducer;
import com.aihub.image.provider.ImageProvider;
import com.aihub.image.provider.ImageProviderRouter;
import com.aihub.image.service.ImageService;
import com.aihub.image.vo.ImageGenerateVO;
import com.aihub.image.vo.ImageTaskVO;
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
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageProviderRouter providerRouter;
    private final ImageTaskMapper imageTaskMapper;
    private final ImageMessageProducer messageProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MinioUtil minioUtil;

    private static final int TOKEN_COST_IMAGE = 50;

    @Override
    @Transactional
    public ImageGenerateVO generate(ImageGenerateRequest request, Long userId) {
        String key = RedisKey.tokenBalance(userId);
        Long balance = redisTemplate.opsForValue().decrement(key, TOKEN_COST_IMAGE);
        if (balance != null && balance < 0) {
            redisTemplate.opsForValue().increment(key, TOKEN_COST_IMAGE);
            throw BusinessException.badRequest("Token余额不足，生成图片需要" + TOKEN_COST_IMAGE + " Token");
        }

        ImageTask task = new ImageTask();
        task.setUserId(userId);
        task.setPrompt(request.getPrompt());
        task.setNegativePrompt(request.getNegativePrompt());
        task.setModel(request.getModel());
        task.setWidth(request.getWidth());
        task.setHeight(request.getHeight());
        task.setStatus(0);
        task.setTokenCost(TOKEN_COST_IMAGE);
        imageTaskMapper.insert(task);

        messageProducer.sendImageTask(task);

        ImageGenerateVO vo = new ImageGenerateVO();
        vo.setTaskId(task.getId());
        vo.setStatus("WAITING");
        vo.setTokenCost(TOKEN_COST_IMAGE);
        vo.setTokenBalance(balance != null ? balance : 0);
        return vo;
    }

    @Override
    public ImageTaskVO getTask(Long taskId, Long userId) {
        ImageTask task = imageTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw BusinessException.notFound();
        }
        return toVO(task);
    }

    @Override
    public List<ImageTaskVO> getTasks(Long userId) {
        List<ImageTask> tasks = imageTaskMapper.selectList(
                new LambdaQueryWrapper<ImageTask>()
                        .eq(ImageTask::getUserId, userId)
                        .orderByDesc(ImageTask::getCreateTime));
        return tasks.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void retryTask(Long taskId, Long userId) {
        ImageTask task = imageTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw BusinessException.notFound();
        }
        if (task.getStatus() != 3) {
            throw BusinessException.badRequest("只有失败的任务可以重试");
        }
        task.setStatus(0);
        imageTaskMapper.updateById(task);
        messageProducer.sendImageTask(task);
    }

    /**
     * MQ消费者回调 - 执行实际的 AI 图片生成
     */
    @Transactional
    public void processImageTask(Long taskId) {
        ImageTask task = imageTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 0) {
            return;
        }

        task.setStatus(1);
        imageTaskMapper.updateById(task);

        try {
            ImageProvider provider = providerRouter.route(task.getModel());
            ImageResult result = provider.generate(task);

            if (result.isSuccess() && result.getImageUrls() != null && !result.getImageUrls().isEmpty()) {
                task.setResultUrl(downloadAndUploadToMinio(result.getImageUrls().get(0), task));
                task.setStatus(2);
            } else {
                task.setStatus(3);
                refundToken(task.getUserId(), TOKEN_COST_IMAGE);
                log.error("图片生成失败: taskId={}, error={}", taskId, result.getError());
            }
        } catch (Exception e) {
            log.error("图片生成异常: taskId={}", taskId, e);
            task.setStatus(3);
            refundToken(task.getUserId(), TOKEN_COST_IMAGE);
        }

        imageTaskMapper.updateById(task);
    }

    private String downloadAndUploadToMinio(String imageUrl, ImageTask task) {
        try {
            if (imageUrl.startsWith("data:image/png;base64,")) {
                String base64 = imageUrl.substring("data:image/png;base64,".length());
                byte[] bytes = Base64.getDecoder().decode(base64);
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                return minioUtil.uploadImage(bis, bytes.length, "image/png", "generated.png");
            }

            URL url = new URL(imageUrl);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            try (InputStream is = conn.getInputStream()) {
                byte[] bytes = IoUtil.readBytes(is);
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                String contentType = conn.getContentType() != null ? conn.getContentType() : "image/png";
                return minioUtil.uploadImage(bis, bytes.length, contentType, "generated.png");
            }
        } catch (Exception e) {
            log.error("下载/上传图片失败: {}", imageUrl, e);
            return imageUrl;
        }
    }

    private void refundToken(Long userId, int amount) {
        redisTemplate.opsForValue().increment(RedisKey.tokenBalance(userId), amount);
    }

    private ImageTaskVO toVO(ImageTask task) {
        ImageTaskVO vo = new ImageTaskVO();
        vo.setTaskId(task.getId());
        vo.setPrompt(StrUtil.sub(task.getPrompt(), 0, 100));
        vo.setModel(task.getModel());
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
