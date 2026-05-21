package com.aihub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthLoginRequest {

    @NotBlank(message = "OAuth授权码不能为空")
    private String code;

    @NotBlank(message = "OAuth提供商不能为空")
    private String provider;

    private String redirectUri;
}
