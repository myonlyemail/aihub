package com.aihub.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.aihub.auth.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SMS_CODE_KEY = "aihub:sms:code:";
    private static final String SMS_RATE_KEY = "aihub:sms:rate:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final long RATE_LIMIT_SECONDS = 60;

    @Override
    public void sendCode(String phone) {
        String rateKey = SMS_RATE_KEY + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            throw new RuntimeException("发送过于频繁，请60秒后重试");
        }

        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(SMS_CODE_KEY + phone, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(rateKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        log.info("===== 短信验证码（开发模式）=====");
        log.info("手机号: {}", phone);
        log.info("验证码: {}", code);
        log.info("有效期: {} 分钟", CODE_EXPIRE_MINUTES);
        log.info("================================");
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        String key = SMS_CODE_KEY + phone;
        String storedCode = (String) redisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            return false;
        }
        if (storedCode.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}
