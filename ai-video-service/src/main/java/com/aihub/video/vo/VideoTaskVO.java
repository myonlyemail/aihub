package com.aihub.video.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoTaskVO {

    private Long taskId;
    private String title;
    private String prompt;
    private String model;
    private Integer duration;
    private Integer status;
    private String statusDesc;
    private String resultUrl;
    private Integer tokenCost;
    private LocalDateTime createTime;
}
