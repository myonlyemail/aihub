package com.aihub.image.service.impl;

import com.aihub.common.exception.BusinessException;
import com.aihub.common.util.MinioUtil;
import com.aihub.image.dto.ImageGenerateRequest;
import com.aihub.image.entity.ImageTask;
import com.aihub.image.mapper.ImageTaskMapper;
import com.aihub.image.mq.ImageMessageProducer;
import com.aihub.image.provider.ImageProvider;
import com.aihub.image.provider.ImageProviderRouter;
import com.aihub.image.vo.ImageGenerateVO;
import com.aihub.image.vo.ImageTaskVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageServiceImpl - AI图片生成服务")
class ImageServiceImplTest {

    @Mock private ImageProviderRouter providerRouter;
    @Mock private ImageTaskMapper imageTaskMapper;
    @Mock private ImageMessageProducer messageProducer;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private MinioUtil minioUtil;

    @InjectMocks
    private ImageServiceImpl imageService;

    private ImageGenerateRequest createRequest() {
        ImageGenerateRequest req = new ImageGenerateRequest();
        req.setPrompt("a beautiful landscape");
        req.setModel("stable-diffusion");
        req.setWidth(512);
        req.setHeight(512);
        return req;
    }

    @Nested
    @DisplayName("generate - 生成图片")
    class Generate {

        @Test
        @DisplayName("Token 不足应抛异常并回退余额")
        void shouldThrowWhenInsufficientTokens() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), eq(50L))).thenReturn(-10L);

            assertThatThrownBy(() -> imageService.generate(createRequest(), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Token余额不足");

            verify(valueOps).increment(anyString(), eq(50L));
        }

        @Test
        @DisplayName("成功创建任务：扣除 Token、入库、发 MQ")
        void shouldDeductTokenAndSendMQ() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), eq(50L))).thenReturn(50L);
            when(imageTaskMapper.insert(any(ImageTask.class))).thenReturn(1);

            ImageGenerateVO result = imageService.generate(createRequest(), 1L);

            assertThat(result.getStatus()).isEqualTo("WAITING");
            assertThat(result.getTokenCost()).isEqualTo(50);
            verify(messageProducer).sendImageTask(any(ImageTask.class));
        }

        @Test
        @DisplayName("创建的任务状态为 0 (等待)")
        void shouldCreateTaskWithStatusWaiting() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), eq(50L))).thenReturn(50L);
            when(imageTaskMapper.insert(any(ImageTask.class))).thenReturn(1);

            ArgumentCaptor<ImageTask> captor = ArgumentCaptor.forClass(ImageTask.class);
            imageService.generate(createRequest(), 1L);

            verify(imageTaskMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(0);
            assertThat(captor.getValue().getTokenCost()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("getTask - 查询任务")
    class GetTask {

        @Test
        @DisplayName("任务不存在应抛异常")
        void shouldThrowWhenNotFound() {
            when(imageTaskMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> imageService.getTask(999L, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("任务不属于当前用户应抛异常")
        void shouldThrowWhenNotOwnedByUser() {
            ImageTask task = new ImageTask();
            task.setId(1L);
            task.setUserId(2L); // different user
            when(imageTaskMapper.selectById(1L)).thenReturn(task);

            assertThatThrownBy(() -> imageService.getTask(1L, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("正常查询返回 TaskVO")
        void shouldReturnTaskVO() {
            ImageTask task = new ImageTask();
            task.setId(1L);
            task.setUserId(1L);
            task.setPrompt("test prompt");
            task.setModel("stable-diffusion");
            task.setStatus(2);
            task.setResultUrl("http://minio/image.png");
            task.setTokenCost(50);
            when(imageTaskMapper.selectById(1L)).thenReturn(task);

            ImageTaskVO result = imageService.getTask(1L, 1L);

            assertThat(result.getTaskId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(2);
            assertThat(result.getStatusDesc()).isEqualTo("已完成");
        }
    }

    @Nested
    @DisplayName("retryTask - 重试任务")
    class RetryTask {

        @Test
        @DisplayName("非失败状态不能重试")
        void shouldThrowWhenNotFailed() {
            ImageTask task = new ImageTask();
            task.setId(1L);
            task.setUserId(1L);
            task.setStatus(2); // success
            when(imageTaskMapper.selectById(1L)).thenReturn(task);

            assertThatThrownBy(() -> imageService.retryTask(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("重试");
        }

        @Test
        @DisplayName("失败任务可重试，状态重置为 0 并发 MQ")
        void shouldRetryFailedTask() {
            ImageTask task = new ImageTask();
            task.setId(1L);
            task.setUserId(1L);
            task.setStatus(3); // failed
            when(imageTaskMapper.selectById(1L)).thenReturn(task);
            when(imageTaskMapper.updateById(any(ImageTask.class))).thenReturn(1);

            imageService.retryTask(1L, 1L);

            ArgumentCaptor<ImageTask> captor = ArgumentCaptor.forClass(ImageTask.class);
            verify(imageTaskMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(0);
            verify(messageProducer).sendImageTask(any(ImageTask.class));
        }
    }
}
