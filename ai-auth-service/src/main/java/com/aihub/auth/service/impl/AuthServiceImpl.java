package com.aihub.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.aihub.auth.dto.OAuthLoginRequest;
import com.aihub.auth.dto.SendCodeRequest;
import com.aihub.auth.dto.SmsLoginRequest;
import com.aihub.auth.dto.WechatLoginRequest;
import com.aihub.auth.entity.User;
import com.aihub.auth.mapper.UserMapper;
import com.aihub.auth.service.AuthService;
import com.aihub.auth.service.OAuthService;
import com.aihub.auth.service.SmsService;
import com.aihub.auth.service.WechatService;
import com.aihub.auth.vo.LoginVO;
import com.aihub.common.dto.LoginDTO;
import com.aihub.common.exception.BusinessException;
import com.aihub.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SmsService smsService;
    private final WechatService wechatService;
    private final OAuthService oauthService;

    private static final String WECHAT_OPENID_KEY = "aihub:wechat:openid:";

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, dto.getAccount())
                .or()
                .eq(User::getEmail, dto.getAccount()));

        if (user == null) {
            throw BusinessException.badRequest("账号不存在");
        }
        if (user.getStatus() == 0) {
            throw BusinessException.badRequest("账号已被禁用");
        }
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }

        return buildLoginVO(user);
    }

    @Override
    public LoginVO register(LoginDTO dto) {
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, dto.getAccount())
                .or()
                .eq(User::getEmail, dto.getAccount()));

        if (exists) {
            throw BusinessException.badRequest("账号已存在");
        }

        User user = new User();
        if (dto.getAccount().contains("@")) {
            user.setEmail(dto.getAccount());
        } else {
            user.setMobile(dto.getAccount());
        }
        user.setNickname("用户" + StrUtil.sub(dto.getAccount(), -4, dto.getAccount().length()));
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setTokenBalance(100L);
        user.setVipLevel(0);
        user.setStatus(1);

        userMapper.insert(user);
        return buildLoginVO(user);
    }

    @Override
    public void logout(Long userId) {
        redisTemplate.delete("aihub:user:token:" + userId);
    }

    @Override
    public void sendCode(SendCodeRequest request) {
        smsService.sendCode(request.getPhone());
    }

    @Override
    public LoginVO smsLogin(SmsLoginRequest request) {
        if (!smsService.verifyCode(request.getPhone(), request.getCode())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, request.getPhone()));

        if (user == null) {
            // Auto-register
            user = new User();
            user.setMobile(request.getPhone());
            user.setNickname("用户" + StrUtil.sub(request.getPhone(), -4, request.getPhone().length()));
            user.setPassword(BCrypt.hashpw(request.getPhone()));
            user.setTokenBalance(100L);
            user.setVipLevel(0);
            user.setStatus(1);
            userMapper.insert(user);
        } else if (user.getStatus() == 0) {
            throw BusinessException.badRequest("账号已被禁用");
        }

        return buildLoginVO(user);
    }

    @Override
    public LoginVO wechatLogin(WechatLoginRequest request) {
        Map<String, String> session = wechatService.getWechatSession(request.getCode());
        String openid = session.get("openid");

        if (openid == null) {
            throw BusinessException.badRequest("微信登录失败：无法获取openid");
        }

        // Look up user by openid mapping in Redis
        String openidKey = WECHAT_OPENID_KEY + openid;
        Long userId = (Long) redisTemplate.opsForValue().get(openidKey);

        User user;
        if (userId != null) {
            user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == 0) {
                throw BusinessException.badRequest("账号已被禁用");
            }
        } else {
            // Create new user for WeChat openid
            user = new User();
            user.setNickname("微信用户" + StrUtil.sub(openid, -6, openid.length()));
            user.setPassword(BCrypt.hashpw(openid));
            user.setTokenBalance(100L);
            user.setVipLevel(0);
            user.setStatus(1);
            userMapper.insert(user);
            redisTemplate.opsForValue().set(openidKey, user.getId());
        }

        return buildLoginVO(user);
    }

    @Override
    public LoginVO oauthLogin(OAuthLoginRequest request) {
        Map<String, Object> oauthUser = oauthService.getOAuthUserInfo(
                request.getProvider(), request.getCode(), request.getRedirectUri());

        String openid = (String) oauthUser.get("openid");
        String nickname = (String) oauthUser.getOrDefault("nickname", "OAuth用户");
        String avatar = (String) oauthUser.getOrDefault("avatar", "");
        String email = (String) oauthUser.getOrDefault("email", "");

        if (openid == null) {
            throw BusinessException.badRequest("OAuth登录失败：无法获取用户标识");
        }

        String openidKey = "aihub:oauth:" + request.getProvider() + ":openid:" + openid;
        Long userId = (Long) redisTemplate.opsForValue().get(openidKey);

        User user;
        if (userId != null) {
            user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == 0) {
                throw BusinessException.badRequest("账号已被禁用");
            }
            if (StrUtil.isNotBlank(avatar)) {
                user.setAvatar(avatar);
                userMapper.updateById(user);
            }
        } else {
            user = new User();
            user.setNickname(nickname);
            user.setAvatar(avatar);
            if (StrUtil.isNotBlank(email)) {
                user.setEmail(email);
            }
            user.setPassword(BCrypt.hashpw(openid));
            user.setTokenBalance(100L);
            user.setVipLevel(0);
            user.setStatus(1);
            userMapper.insert(user);
            redisTemplate.opsForValue().set(openidKey, user.getId());
        }

        return buildLoginVO(user);
    }

    private LoginVO buildLoginVO(User user) {
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        String token = JwtUtil.createToken(user.getId(), user.getTenantId());
        redisTemplate.opsForValue().set("aihub:user:token:" + user.getId(), token, 30, TimeUnit.DAYS);

        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setToken(token);
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setVipLevel(user.getVipLevel());
        vo.setTokenBalance(user.getTokenBalance());
        return vo;
    }
}
