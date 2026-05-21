package com.aihub.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoGenerateRequest {

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    private String title;
    private String model = "runway";
    private Integer duration = 5;
    private String resolution = "1080p";
    private String referenceImage;
}
