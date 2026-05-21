package com.aihub.auth.controller;

import com.aihub.auth.dto.OAuthLoginRequest;
import com.aihub.auth.dto.SendCodeRequest;
import com.aihub.auth.dto.SmsLoginRequest;
import com.aihub.auth.dto.WechatLoginRequest;
import com.aihub.auth.service.AuthService;
import com.aihub.auth.vo.LoginVO;
import com.aihub.common.dto.LoginDTO;
import com.aihub.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证服务", description = "登录/注册/登出/验证码/第三方登录")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "密码登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.register(dto));
    }

    @Operation(summary = "发送短信验证码")
    @PostMapping("/send-code")
    public Result<?> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request);
        return Result.success();
    }

    @Operation(summary = "短信验证码登录")
    @PostMapping("/sms-login")
    public Result<LoginVO> smsLogin(@Valid @RequestBody SmsLoginRequest request) {
        return Result.success(authService.smsLogin(request));
    }

    @Operation(summary = "微信小程序登录")
    @PostMapping("/wechat-login")
    public Result<LoginVO> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return Result.success(authService.wechatLogin(request));
    }

    @Operation(summary = "OAuth2第三方登录 (Google/GitHub)")
    @PostMapping("/oauth-login")
    public Result<LoginVO> oauthLogin(@Valid @RequestBody OAuthLoginRequest request) {
        return Result.success(authService.oauthLogin(request));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<?> logout(@Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        authService.logout(userId);
        return Result.success();
    }
}
