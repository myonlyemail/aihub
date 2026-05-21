package com.aihub.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionVO {

    private Long sessionId;
    private String title;
    private String model;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
