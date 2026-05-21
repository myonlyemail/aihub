package com.aihub.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDetailVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer vipLevel;
    private String vipLevelDesc;
    private Long tokenBalance;
    private Integer status;
    private String statusDesc;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private Long chatCount;
    private Long imageCount;
}
