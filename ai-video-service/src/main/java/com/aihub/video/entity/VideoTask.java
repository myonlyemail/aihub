package com.aihub.video.entity;

import com.aihub.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_video_task")
public class VideoTask extends BaseEntity {

    private Long userId;
    private String title;
    private String prompt;
    private String model;
    private Integer duration;
    private Integer status;
    private String resultUrl;
    private Integer tokenCost;
}
