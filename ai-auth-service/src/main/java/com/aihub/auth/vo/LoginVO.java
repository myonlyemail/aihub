package com.aihub.auth.vo;

import lombok.Data;

@Data
public class LoginVO {
    private Long userId;
    private String token;
    private String nickname;
    private String avatar;
    private Integer vipLevel;
    private Long tokenBalance;
}
