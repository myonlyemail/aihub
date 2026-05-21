package com.aihub.image.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImageGenerateRequest {

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    private String negativePrompt;
    private String model = "stable-diffusion";
    private Integer width = 1024;
    private Integer height = 1024;
    private Integer numImages = 1;
    private String style;
    private String referenceImage;
}
