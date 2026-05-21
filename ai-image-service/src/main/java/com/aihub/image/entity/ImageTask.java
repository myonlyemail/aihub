package com.aihub.image.entity;

import com.aihub.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_image_task")
public class ImageTask extends BaseEntity {

    private Long userId;
    private String prompt;
    private String negativePrompt;
    private String model;
    private Integer width;
    private Integer height;
    private Integer status;
    private String resultUrl;
    private Integer tokenCost;
}
