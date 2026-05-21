package com.aihub.video.vo;

import lombok.Data;

@Data
public class VideoGenerateVO {

    private Long taskId;
    private String status;
    private String resultUrl;
    private Integer tokenCost;
    private Long tokenBalance;
}
