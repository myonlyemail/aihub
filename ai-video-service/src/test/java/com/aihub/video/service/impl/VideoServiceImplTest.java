package com.aihub.video.service.impl;

import com.aihub.common.exception.BusinessException;
import com.aihub.common.util.MinioUtil;
import com.aihub.video.dto.VideoGenerateRequest;
import com.aihub.video.entity.VideoTask;
import com.aihub.video.mapper.VideoTaskMapper;
import com.aihub.video.mq.VideoMessageProducer;
import com.aihub.video.provider.VideoProviderRouter;
import com.aihub.video.vo.VideoGenerateVO;
import com.aihub.video.vo.VideoTaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VideoServiceImpl - AI视频生成服务")
class VideoServiceImplTest {

    @Mock private VideoProviderRouter providerRouter;
    @Mock private VideoTaskMapper videoTaskMapper;
    @Mock private VideoMessageProducer messageProducer;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private MinioUtil minioUtil;

    @InjectMocks
    private VideoServiceImpl videoService;

    @Nested
    @DisplayName("generate - 生成视频")
    class Generate {

        @Test
        @DisplayName("Token 不足应抛异常并回退（视频消耗 200 Token）")
        void shouldThrowWhenInsufficientTokens() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), eq(200L))).thenReturn(-10L);

            VideoGenerateRequest req = new VideoGenerateRequest();
            req.setPrompt("a sunset over the ocean");

            assertThatThrownBy(() -> videoService.generate(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Token余额不足");

            verify(valueOps).increment(anyString(), eq(200L));
        }

        @Test
        @DisplayName("成功创建视频任务，Token 消耗 200")
        void shouldCreateTaskWith200TokenCost() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), eq(200L))).thenReturn(300L);
            when(videoTaskMapper.insert(any(VideoTask.class))).thenReturn(1);

            VideoGenerateRequest req = new VideoGenerateRequest();
            req.setPrompt("a sunset over the ocean");
            req.setModel("runway");

            VideoGenerateVO result = videoService.generate(req, 1L);

            assertThat(result.getStatus()).isEqualTo("WAITING");
            assertThat(result.getTokenCost()).isEqualTo(200);
            verify(messageProducer).sendVideoTask(any(VideoTask.class));
        }
    }

    @Nested
    @DisplayName("getTask - 查询任务")
    class GetTask {

        @Test
        @DisplayName("任务不属于当前用户应抛异常")
        void shouldThrowWhenNotOwned() {
            VideoTask task = new VideoTask();
            task.setId(1L);
            task.setUserId(99L);
            when(videoTaskMapper.selectById(1L)).thenReturn(task);

            assertThatThrownBy(() -> videoService.getTask(1L, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("正常查询返回 TaskVO")
        void shouldReturnTaskVO() {
            VideoTask task = new VideoTask();
            task.setId(1L);
            task.setUserId(1L);
            task.setPrompt("a sunset over the ocean");
            task.setModel("runway");
            task.setStatus(2);
            task.setResultUrl("http://minio/video.mp4");
            task.setTokenCost(200);
            when(videoTaskMapper.selectById(1L)).thenReturn(task);

            VideoTaskVO result = videoService.getTask(1L, 1L);

            assertThat(result.getTaskId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(2);
            assertThat(result.getStatusDesc()).isEqualTo("已完成");
            assertThat(result.getTokenCost()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("retryTask - 重试任务")
    class RetryTask {

        @Test
        @DisplayName("已完成的任务不能重试")
        void shouldThrowWhenNotFailed() {
            VideoTask task = new VideoTask();
            task.setId(1L);
            task.setUserId(1L);
            task.setStatus(2);
            when(videoTaskMapper.selectById(1L)).thenReturn(task);

            assertThatThrownBy(() -> videoService.retryTask(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("重试");
        }

        @Test
        @DisplayName("失败任务可重试并发 MQ")
        void shouldRetryFailedTask() {
            VideoTask task = new VideoTask();
            task.setId(1L);
            task.setUserId(1L);
            task.setStatus(3);
            when(videoTaskMapper.selectById(1L)).thenReturn(task);
            when(videoTaskMapper.updateById(any(VideoTask.class))).thenReturn(1);

            videoService.retryTask(1L, 1L);

            verify(videoTaskMapper).updateById(any(VideoTask.class));
            verify(messageProducer).sendVideoTask(any(VideoTask.class));
        }
    }
}
