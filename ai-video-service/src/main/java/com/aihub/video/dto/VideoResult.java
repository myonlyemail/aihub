package com.aihub.video.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoResult {

    private boolean success;
    private String videoUrl;
    private String model;
    private String error;
    private Integer tokenCost;
}
