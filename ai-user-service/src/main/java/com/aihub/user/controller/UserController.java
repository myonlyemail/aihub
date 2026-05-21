package com.aihub.user.controller;

import com.aihub.common.result.PageResult;
import com.aihub.common.result.Result;
import com.aihub.user.dto.ChangePasswordRequest;
import com.aihub.user.dto.UpdateProfileRequest;
import com.aihub.user.service.UserService;
import com.aihub.user.vo.TokenLogVO;
import com.aihub.user.vo.UserInfoVO;
import com.aihub.user.vo.VipInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户服务", description = "个人信息/Token余额/VIP/密码修改")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取个人信息")
    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestHeader("X-User-Id") Long userId,
                                   @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(userId, request);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<?> changePassword(@RequestHeader("X-User-Id") Long userId,
                                    @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return Result.success();
    }

    @Operation(summary = "查询Token余额")
    @GetMapping("/token/balance")
    public Result<Long> getTokenBalance(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getTokenBalance(userId));
    }

    @Operation(summary = "Token流水")
    @GetMapping("/token/logs")
    public Result<PageResult<TokenLogVO>> getTokenLogs(@RequestHeader("X-User-Id") Long userId,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(userService.getTokenLogs(userId, page, size));
    }

    @Operation(summary = "VIP信息")
    @GetMapping("/vip")
    public Result<VipInfoVO> getVipInfo(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getVipInfo(userId));
    }
}
