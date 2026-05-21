package com.aihub.admin.dto;

import lombok.Data;

@Data
public class UserQueryRequest {

    private String keyword;
    private Integer vipLevel;
    private Integer status;
    private Integer page = 1;
    private Integer size = 20;
}
