package com.aihub.auth.service;

import com.aihub.auth.dto.OAuthLoginRequest;
import com.aihub.auth.dto.SendCodeRequest;
import com.aihub.auth.dto.SmsLoginRequest;
import com.aihub.auth.dto.WechatLoginRequest;
import com.aihub.auth.vo.LoginVO;
import com.aihub.common.dto.LoginDTO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    LoginVO register(LoginDTO dto);

    void logout(Long userId);

    void sendCode(SendCodeRequest request);

    LoginVO smsLogin(SmsLoginRequest request);

    LoginVO wechatLogin(WechatLoginRequest request);

    LoginVO oauthLogin(OAuthLoginRequest request);
}
