package com.aihub.common.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;

import java.util.Map;

public class JwtUtil {

    private static final String SECRET = "aihub-secret-key-2024";

    public static String createToken(Long userId) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("iat", System.currentTimeMillis())
                .setSigner(JWTSignerUtil.hs256(SECRET.getBytes()))
                .sign();
    }

    public static String createToken(Long userId, Long tenantId) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("tenantId", tenantId)
                .setPayload("iat", System.currentTimeMillis())
                .setSigner(JWTSignerUtil.hs256(SECRET.getBytes()))
                .sign();
    }

    public static boolean verify(String token) {
        try {
            return JWTUtil.verify(token, JWTSignerUtil.hs256(SECRET.getBytes()));
        } catch (Exception e) {
            return false;
        }
    }

    public static Long getUserId(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            Object userId = jwt.getPayload("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            }
            return Long.valueOf(userId.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static Long getTenantId(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            Object tenantId = jwt.getPayload("tenantId");
            if (tenantId == null) {
                return null;
            }
            if (tenantId instanceof Long) {
                return (Long) tenantId;
            }
            return Long.valueOf(tenantId.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
