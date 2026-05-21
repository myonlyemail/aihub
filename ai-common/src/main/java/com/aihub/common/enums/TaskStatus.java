package com.aihub.common.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {

    WAITING(0, "等待中"),
    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAIL(3, "失败");

    private final Integer code;
    private final String desc;

    TaskStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
