package com.aihub.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VipInfoVO {

    private Integer vipLevel;
    private String vipLevelDesc;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean isExpired;
    private Long remainDays;
}
