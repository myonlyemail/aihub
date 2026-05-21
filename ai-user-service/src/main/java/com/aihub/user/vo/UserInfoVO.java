package com.aihub.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer vipLevel;
    private String vipLevelDesc;
    private Long tokenBalance;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
