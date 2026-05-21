package com.aihub.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "手机号/邮箱不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
