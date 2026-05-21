package com.aihub.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenLogVO {

    private Long id;
    private String businessType;
    private Integer tokenChange;
    private Integer remainToken;
    private String remark;
    private LocalDateTime createTime;
}
