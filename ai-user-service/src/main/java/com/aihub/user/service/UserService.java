package com.aihub.user.service;

import com.aihub.common.result.PageResult;
import com.aihub.user.dto.ChangePasswordRequest;
import com.aihub.user.dto.UpdateProfileRequest;
import com.aihub.user.vo.TokenLogVO;
import com.aihub.user.vo.UserInfoVO;
import com.aihub.user.vo.VipInfoVO;

public interface UserService {

    UserInfoVO getUserInfo(Long userId);

    void updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    Long getTokenBalance(Long userId);

    PageResult<TokenLogVO> getTokenLogs(Long userId, Integer page, Integer size);

    VipInfoVO getVipInfo(Long userId);
}
