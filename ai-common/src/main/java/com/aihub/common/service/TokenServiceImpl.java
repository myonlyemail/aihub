package com.aihub.common.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
public class TokenServiceImpl implements TokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    public TokenServiceImpl(RedisTemplate<String, Object> redisTemplate, RedissonClient redissonClient) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean tryAcquireLock(String key, long timeout, TimeUnit unit) {
        RLock lock = redissonClient.getLock("aihub:lock:" + key);
        try {
            return lock.tryLock(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void releaseLock(String key) {
        RLock lock = redissonClient.getLock("aihub:lock:" + key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public long deductToken(String key, long amount) {
        Long result = redisTemplate.opsForValue().decrement(key, amount);
        return result != null ? result : 0;
    }

    @Override
    public long getTokenBalance(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    @Override
    public void addToken(String key, long amount) {
        redisTemplate.opsForValue().increment(key, amount);
    }
}
