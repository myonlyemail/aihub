package com.aihub.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public static BusinessException of(String message) {
        return new BusinessException(message);
    }

    public static BusinessException unauthorized() {
        return new BusinessException(401, "未登录或登录已过期");
    }

    public static BusinessException forbidden() {
        return new BusinessException(403, "无权限访问");
    }

    public static BusinessException notFound() {
        return new BusinessException(404, "资源不存在");
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }
}
