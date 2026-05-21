package com.aihub.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.aihub.auth.dto.SendCodeRequest;
import com.aihub.auth.dto.SmsLoginRequest;
import com.aihub.auth.dto.WechatLoginRequest;
import com.aihub.auth.dto.OAuthLoginRequest;
import com.aihub.auth.entity.User;
import com.aihub.auth.mapper.UserMapper;
import com.aihub.auth.service.OAuthService;
import com.aihub.auth.service.SmsService;
import com.aihub.auth.service.WechatService;
import com.aihub.auth.vo.LoginVO;
import com.aihub.common.dto.LoginDTO;
import com.aihub.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl - 认证服务")
class AuthServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private SmsService smsService;
    @Mock private WechatService wechatService;
    @Mock private OAuthService oauthService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    @DisplayName("login - 账号密码登录")
    class Login {

        @Test
        @DisplayName("账号不存在应抛异常")
        void shouldThrowWhenAccountNotFound() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800000000");
            dto.setPassword("password");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号不存在");
        }

        @Test
        @DisplayName("账号被禁用应抛异常")
        void shouldThrowWhenDisabled() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800000000");
            dto.setPassword("password");

            User user = new User();
            user.setId(1L);
            user.setStatus(0);
            user.setPassword(BCrypt.hashpw("password"));
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("被禁用");
        }

        @Test
        @DisplayName("密码错误应抛异常")
        void shouldThrowWhenWrongPassword() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800000000");
            dto.setPassword("wrongpassword");

            User user = new User();
            user.setId(1L);
            user.setStatus(1);
            user.setPassword(BCrypt.hashpw("correctpassword"));
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("密码错误");
        }

        @Test
        @DisplayName("登录成功返回 LoginVO")
        void shouldReturnLoginVOWhenSuccess() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800000000");
            dto.setPassword("password");

            User user = new User();
            user.setId(1L);
            user.setStatus(1);
            user.setNickname("TestUser");
            user.setPassword(BCrypt.hashpw("password"));
            user.setTokenBalance(100L);
            user.setVipLevel(0);
            user.setTenantId(0L);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            LoginVO result = authService.login(dto);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getNickname()).isEqualTo("TestUser");
            assertThat(result.getToken()).isNotNull().isNotEmpty();
            verify(valueOps).set(contains("token"), anyString(), eq(30L), any());
        }
    }

    @Nested
    @DisplayName("register - 注册")
    class Register {

        @Test
        @DisplayName("手机号已存在应抛异常")
        void shouldThrowWhenMobileExists() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800000000");
            dto.setPassword("password123");

            when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            assertThatThrownBy(() -> authService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在");
        }

        @Test
        @DisplayName("邮箱已存在应抛异常")
        void shouldThrowWhenEmailExists() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("user@example.com");
            dto.setPassword("password123");

            when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            assertThatThrownBy(() -> authService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在");
        }

        @Test
        @DisplayName("注册成功，新用户默认 100 token")
        void shouldRegisterWithDefaultTokens() {
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800000000");
            dto.setPassword("password123");

            when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            LoginVO result = authService.register(dto);

            verify(userMapper).insert(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getTokenBalance()).isEqualTo(100L);
            assertThat(savedUser.getVipLevel()).isEqualTo(0);
            assertThat(savedUser.getStatus()).isEqualTo(1);
            assertThat(result.getTokenBalance()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("smsLogin - 短信验证码登录")
    class SmsLogin {

        @Test
        @DisplayName("验证码错误应抛异常")
        void shouldThrowWhenCodeIncorrect() {
            SmsLoginRequest request = new SmsLoginRequest();
            request.setPhone("13800000000");
            request.setCode("000000");

            when(smsService.verifyCode("13800000000", "000000")).thenReturn(false);

            assertThatThrownBy(() -> authService.smsLogin(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("验证码");
        }

        @Test
        @DisplayName("验证成功且用户不存在，自动注册新用户")
        void shouldAutoRegisterWhenUserNotFound() {
            SmsLoginRequest request = new SmsLoginRequest();
            request.setPhone("13800000000");
            request.setCode("123456");

            when(smsService.verifyCode("13800000000", "123456")).thenReturn(true);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            LoginVO result = authService.smsLogin(request);

            assertThat(result).isNotNull();
            verify(userMapper).insert(any(User.class));
        }

        @Test
        @DisplayName("已有用户但被禁用应抛异常")
        void shouldThrowWhenExistingUserDisabled() {
            SmsLoginRequest request = new SmsLoginRequest();
            request.setPhone("13800000000");
            request.setCode("123456");

            User user = new User();
            user.setId(1L);
            user.setStatus(0);

            when(smsService.verifyCode("13800000000", "123456")).thenReturn(true);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            assertThatThrownBy(() -> authService.smsLogin(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("被禁用");
        }
    }

    @Nested
    @DisplayName("wechatLogin - 微信登录")
    class WechatLogin {

        @Test
        @DisplayName("无法获取 openid 应抛异常")
        void shouldThrowWhenNoOpenId() {
            WechatLoginRequest request = new WechatLoginRequest();
            request.setCode("invalid-code");

            when(wechatService.getWechatSession("invalid-code")).thenReturn(Map.of());

            assertThatThrownBy(() -> authService.wechatLogin(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("openid");
        }

        @Test
        @DisplayName("新微信用户自动创建")
        void shouldCreateNewUserForNewOpenId() {
            WechatLoginRequest request = new WechatLoginRequest();
            request.setCode("valid-code");

            when(wechatService.getWechatSession("valid-code")).thenReturn(Map.of("openid", "wx-openid-123"));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:wechat:openid:wx-openid-123")).thenReturn(null);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            LoginVO result = authService.wechatLogin(request);

            assertThat(result).isNotNull();
            verify(userMapper).insert(any(User.class));
            verify(valueOps).set(eq("aihub:wechat:openid:wx-openid-123"), any());
        }
    }

    @Nested
    @DisplayName("oauthLogin - OAuth 登录")
    class OauthLogin {

        @Test
        @DisplayName("无法获取 openid 应抛异常")
        void shouldThrowWhenNoOpenIdFromOAuth() {
            OAuthLoginRequest request = new OAuthLoginRequest();
            request.setProvider("google");
            request.setCode("test-code");

            when(oauthService.getOAuthUserInfo("google", "test-code", null)).thenReturn(Map.of("nickname", "Test"));

            assertThatThrownBy(() -> authService.oauthLogin(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OAuth");
        }

        @Test
        @DisplayName("新 OAuth 用户自动创建")
        void shouldCreateNewUserForNewOAuthOpenId() {
            OAuthLoginRequest request = new OAuthLoginRequest();
            request.setProvider("google");
            request.setCode("test-code");

            when(oauthService.getOAuthUserInfo("google", "test-code", null))
                    .thenReturn(Map.of("openid", "oauth-123", "nickname", "GoogleUser", "email", "g@example.com"));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:oauth:google:openid:oauth-123")).thenReturn(null);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            LoginVO result = authService.oauthLogin(request);

            assertThat(result).isNotNull();
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            assertThat(userCaptor.getValue().getNickname()).isEqualTo("GoogleUser");
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("g@example.com");
        }
    }
}
