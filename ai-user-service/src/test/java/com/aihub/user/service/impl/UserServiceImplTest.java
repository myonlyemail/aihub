package com.aihub.user.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.aihub.common.exception.BusinessException;
import com.aihub.user.dto.ChangePasswordRequest;
import com.aihub.user.dto.UpdateProfileRequest;
import com.aihub.user.entity.User;
import com.aihub.user.entity.UserVip;
import com.aihub.user.mapper.TokenLogMapper;
import com.aihub.user.mapper.UserMapper;
import com.aihub.user.mapper.UserVipMapper;
import com.aihub.user.vo.UserInfoVO;
import com.aihub.user.vo.VipInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl - 用户服务")
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private TokenLogMapper tokenLogMapper;
    @Mock private UserVipMapper userVipMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private UserServiceImpl userService;

    private User createUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setMobile("1380000000" + id);
        user.setEmail("user" + id + "@test.com");
        user.setPassword(BCrypt.hashpw("password"));
        user.setTokenBalance(100L);
        user.setVipLevel(0);
        user.setStatus(1);
        return user;
    }

    @Nested
    @DisplayName("getUserInfo - 获取用户信息")
    class GetUserInfo {

        @Test
        @DisplayName("用户不存在应抛异常")
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> userService.getUserInfo(999L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("正常返回用户信息，含 VIP 等级描述")
        void shouldReturnUserInfoWithVipDesc() {
            User user = createUser(1L, "TestUser");
            user.setVipLevel(1);
            when(userMapper.selectById(1L)).thenReturn(user);

            UserInfoVO result = userService.getUserInfo(1L);

            assertThat(result.getNickname()).isEqualTo("TestUser");
            assertThat(result.getVipLevelDesc()).isEqualTo("VIP会员");
            assertThat(result.getTokenBalance()).isEqualTo(100L);
        }

        @Test
        @DisplayName("免费用户显示对应描述")
        void shouldShowFreeUserDesc() {
            User user = createUser(1L, "FreeUser");
            when(userMapper.selectById(1L)).thenReturn(user);

            UserInfoVO result = userService.getUserInfo(1L);
            assertThat(result.getVipLevelDesc()).isEqualTo("免费用户");
        }
    }

    @Nested
    @DisplayName("updateProfile - 更新资料")
    class UpdateProfile {

        @Test
        @DisplayName("用户不存在应抛异常")
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> userService.updateProfile(999L, new UpdateProfileRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("邮箱被他人使用应抛异常")
        void shouldThrowWhenEmailTaken() {
            User current = createUser(1L, "User");
            when(userMapper.selectById(1L)).thenReturn(current);

            User other = new User();
            other.setId(2L);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(other);

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("other@test.com");

            assertThatThrownBy(() -> userService.updateProfile(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邮箱");
        }

        @Test
        @DisplayName("自己更新相同邮箱不抛异常")
        void shouldAllowSameEmailForSelf() {
            User current = createUser(1L, "User");
            when(userMapper.selectById(1L)).thenReturn(current);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("newemail@test.com");

            userService.updateProfile(1L, req);
            verify(userMapper).updateById(any(User.class));
        }
    }

    @Nested
    @DisplayName("changePassword - 修改密码")
    class ChangePassword {

        @Test
        @DisplayName("原密码错误应抛异常")
        void shouldThrowWhenOldPasswordWrong() {
            User user = createUser(1L, "User");
            when(userMapper.selectById(1L)).thenReturn(user);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("wrongpassword");
            req.setNewPassword("newpassword");

            assertThatThrownBy(() -> userService.changePassword(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("原密码");
        }

        @Test
        @DisplayName("修改成功应删除 Redis token 缓存")
        void shouldDeleteTokenCacheOnSuccess() {
            User user = createUser(1L, "User");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("password");
            req.setNewPassword("newpassword");

            userService.changePassword(1L, req);

            verify(redisTemplate).delete("aihub:user:token:1");
        }
    }

    @Nested
    @DisplayName("getTokenBalance - Token 余额")
    class GetTokenBalance {

        @Test
        @DisplayName("Redis 缓存命中直接返回")
        void shouldReturnCachedValue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:token:balance:1")).thenReturn(200L);

            assertThat(userService.getTokenBalance(1L)).isEqualTo(200L);
            verify(userMapper, never()).selectById(anyLong());
        }
    }

    @Nested
    @DisplayName("getVipInfo - VIP 信息")
    class GetVipInfo {

        @Test
        @DisplayName("VIP 逾期返回 isExpired=true 且 remainDays=0")
        void shouldMarkExpiredVip() {
            User user = createUser(1L, "User");
            user.setVipLevel(1);
            when(userMapper.selectById(1L)).thenReturn(user);

            UserVip vip = new UserVip();
            vip.setStartTime(LocalDateTime.now().minusDays(60));
            vip.setEndTime(LocalDateTime.now().minusDays(1));
            when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(vip);

            VipInfoVO result = userService.getVipInfo(1L);

            assertThat(result.getIsExpired()).isTrue();
            assertThat(result.getRemainDays()).isEqualTo(0L);
        }

        @Test
        @DisplayName("SVIP 用户显示对应描述")
        void shouldShowSvipDesc() {
            User user = createUser(1L, "User");
            user.setVipLevel(2);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            VipInfoVO result = userService.getVipInfo(1L);

            assertThat(result.getVipLevelDesc()).isEqualTo("SVIP会员");
        }
    }
}
