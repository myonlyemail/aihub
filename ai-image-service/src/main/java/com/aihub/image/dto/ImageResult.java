package com.aihub.image.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImageResult {

    private boolean success;
    private List<String> imageUrls;
    private String model;
    private String error;
    private Integer tokenCost;
}
