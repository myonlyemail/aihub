package com.aihub.common.enums;

import lombok.Getter;

@Getter
public enum VipLevel {

    FREE(0, "免费用户"),
    VIP(1, "VIP会员"),
    SVIP(2, "SVIP会员"),
    ENTERPRISE(9, "企业版");

    private final Integer code;
    private final String desc;

    VipLevel(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
