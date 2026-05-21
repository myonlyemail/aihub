package com.aihub.image.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageTaskVO {

    private Long taskId;
    private String prompt;
    private String model;
    private Integer status;
    private String statusDesc;
    private String resultUrl;
    private Integer tokenCost;
    private LocalDateTime createTime;
}
