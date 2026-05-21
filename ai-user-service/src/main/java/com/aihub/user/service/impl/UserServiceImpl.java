package com.aihub.user.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.aihub.common.enums.VipLevel;
import com.aihub.common.exception.BusinessException;
import com.aihub.common.result.PageResult;
import com.aihub.user.dto.ChangePasswordRequest;
import com.aihub.user.dto.UpdateProfileRequest;
import com.aihub.user.entity.TokenLog;
import com.aihub.user.entity.User;
import com.aihub.user.entity.UserVip;
import com.aihub.user.mapper.TokenLogMapper;
import com.aihub.user.mapper.UserMapper;
import com.aihub.user.mapper.UserVipMapper;
import com.aihub.user.service.UserService;
import com.aihub.user.vo.TokenLogVO;
import com.aihub.user.vo.UserInfoVO;
import com.aihub.user.vo.VipInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final TokenLogMapper tokenLogMapper;
    private final UserVipMapper userVipMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        return toUserInfoVO(user);
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getEmail() != null) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, request.getEmail())
                    .ne(User::getId, userId));
            if (exist != null) {
                throw BusinessException.badRequest("邮箱已被使用");
            }
            user.setEmail(request.getEmail());
        }
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw BusinessException.badRequest("原密码错误");
        }
        user.setPassword(BCrypt.hashpw(request.getNewPassword()));
        userMapper.updateById(user);

        redisTemplate.delete("aihub:user:token:" + userId);
    }

    @Override
    public Long getTokenBalance(Long userId) {
        String balanceKey = "aihub:token:balance:" + userId;
        Object cached = redisTemplate.opsForValue().get(balanceKey);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        redisTemplate.opsForValue().set(balanceKey, user.getTokenBalance());
        return user.getTokenBalance();
    }

    @Override
    public PageResult<TokenLogVO> getTokenLogs(Long userId, Integer pageNum, Integer pageSize) {
        int page = pageNum != null ? pageNum : 1;
        int size = pageSize != null ? pageSize : 20;

        Page<TokenLog> result = tokenLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<TokenLog>()
                        .eq(TokenLog::getUserId, userId)
                        .orderByDesc(TokenLog::getCreateTime));

        List<TokenLogVO> records = result.getRecords().stream().map(log -> {
            TokenLogVO vo = new TokenLogVO();
            vo.setId(log.getId());
            vo.setBusinessType(log.getBusinessType());
            vo.setTokenChange(log.getTokenChange());
            vo.setRemainToken(log.getRemainToken());
            vo.setRemark(log.getRemark());
            vo.setCreateTime(log.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public VipInfoVO getVipInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }

        UserVip vip = userVipMapper.selectOne(new LambdaQueryWrapper<UserVip>()
                .eq(UserVip::getUserId, userId)
                .eq(UserVip::getStatus, 1)
                .orderByDesc(UserVip::getCreateTime)
                .last("LIMIT 1"));

        VipInfoVO vo = new VipInfoVO();
        vo.setVipLevel(user.getVipLevel());

        String desc;
        switch (user.getVipLevel()) {
            case 1 -> desc = "VIP会员";
            case 2 -> desc = "SVIP会员";
            case 9 -> desc = "企业版";
            default -> desc = "免费用户";
        }
        vo.setVipLevelDesc(desc);

        if (vip != null) {
            vo.setStartTime(vip.getStartTime());
            vo.setEndTime(vip.getEndTime());
            if (vip.getEndTime() != null) {
                boolean expired = vip.getEndTime().isBefore(LocalDateTime.now());
                vo.setIsExpired(expired);
                vo.setRemainDays(expired ? 0L : Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), vip.getEndTime())));
            }
        }

        return vo;
    }

    private UserInfoVO toUserInfoVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setVipLevel(user.getVipLevel());
        String desc;
        switch (user.getVipLevel()) {
            case 1 -> desc = "VIP会员";
            case 2 -> desc = "SVIP会员";
            case 9 -> desc = "企业版";
            default -> desc = "免费用户";
        }
        vo.setVipLevelDesc(desc);
        vo.setTokenBalance(user.getTokenBalance());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
