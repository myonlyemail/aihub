package com.aihub.common.service;

import java.util.concurrent.TimeUnit;

public interface TokenService {

    boolean tryAcquireLock(String key, long timeout, TimeUnit unit);

    void releaseLock(String key);

    long deductToken(String key, long amount);

    long getTokenBalance(String key);

    void addToken(String key, long amount);
}
