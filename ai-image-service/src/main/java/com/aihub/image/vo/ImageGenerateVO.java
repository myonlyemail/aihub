package com.aihub.image.vo;

import lombok.Data;

@Data
public class ImageGenerateVO {

    private Long taskId;
    private String status;
    private String resultUrl;
    private Integer tokenCost;
    private Long tokenBalance;
}
