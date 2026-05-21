package com.aihub.auth.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsServiceImpl - 短信验证码服务")
class SmsServiceImplTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private SmsServiceImpl smsService;

    @Nested
    @DisplayName("sendCode - 发送验证码")
    class SendCode {

        @Test
        @DisplayName("60秒内重复发送应抛异常")
        void shouldThrowWhenRateLimited() {
            when(redisTemplate.hasKey("aihub:sms:rate:13800000000")).thenReturn(true);

            assertThatThrownBy(() -> smsService.sendCode("13800000000"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("频繁");
        }

        @Test
        @DisplayName("正常发送应存验证码到 Redis")
        void shouldStoreCodeInRedis() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(redisTemplate.hasKey("aihub:sms:rate:13800000000")).thenReturn(false);

            smsService.sendCode("13800000000");

            verify(valueOps).set(contains("code:138"), anyString(), eq(5L), any());
            verify(valueOps).set(contains("rate:138"), eq("1"), eq(60L), any());
        }
    }

    @Nested
    @DisplayName("verifyCode - 校验验证码")
    class VerifyCode {

        @Test
        @DisplayName("验证码不存在（过期）返回 false")
        void shouldReturnFalseWhenCodeExpired() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:sms:code:13800000000")).thenReturn(null);

            assertThat(smsService.verifyCode("13800000000", "123456")).isFalse();
        }

        @Test
        @DisplayName("验证码匹配则删除并返回 true")
        void shouldDeleteAndReturnTrueWhenMatched() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:sms:code:13800000000")).thenReturn("123456");

            assertThat(smsService.verifyCode("13800000000", "123456")).isTrue();
            verify(redisTemplate).delete("aihub:sms:code:13800000000");
        }

        @Test
        @DisplayName("验证码不匹配返回 false，不删除")
        void shouldReturnFalseWhenMismatched() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:sms:code:13800000000")).thenReturn("999999");

            assertThat(smsService.verifyCode("13800000000", "123456")).isFalse();
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("验证码一次性使用：匹配后第二次验证返回 false")
        void shouldBeOneTimeUse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:sms:code:13800000000")).thenReturn("123456");
            assertThat(smsService.verifyCode("13800000000", "123456")).isTrue();

            when(valueOps.get("aihub:sms:code:13800000000")).thenReturn(null);
            assertThat(smsService.verifyCode("13800000000", "123456")).isFalse();
        }
    }
}
