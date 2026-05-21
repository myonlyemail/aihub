package com.aihub.common.dto;

import lombok.Data;

@Data
public class TokenInfo {
    private Long userId;
    private Long tenantId;
    private String token;
}
