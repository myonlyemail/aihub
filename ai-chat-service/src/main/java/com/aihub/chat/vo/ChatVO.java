package com.aihub.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatVO {

    private Long sessionId;
    private String message;
    private String modelName;
    private Integer tokenCost;
    private Long tokenBalance;
    private LocalDateTime createTime;
}
