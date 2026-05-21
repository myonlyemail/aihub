package com.aihub.common.constant;

public interface RedisKey {

    String USER_TOKEN = "aihub:user:token:";
    String USER_INFO = "aihub:user:info:";
    String TOKEN_BALANCE = "aihub:token:balance:";
    String RATE_LIMIT = "aihub:ratelimit:";
    String DISTRIBUTED_LOCK = "aihub:lock:";
    String CHAT_SESSION = "aihub:chat:session:";

    static String userToken(Long userId) {
        return USER_TOKEN + userId;
    }

    static String userInfo(Long userId) {
        return USER_INFO + userId;
    }

    static String tokenBalance(Long userId) {
        return TOKEN_BALANCE + userId;
    }

    static String rateLimit(String key) {
        return RATE_LIMIT + key;
    }
}
