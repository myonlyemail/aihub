package com.aihub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLoginRequest {

    @NotBlank(message = "微信授权码不能为空")
    private String code;

    private String encryptedData;
    private String iv;
}
