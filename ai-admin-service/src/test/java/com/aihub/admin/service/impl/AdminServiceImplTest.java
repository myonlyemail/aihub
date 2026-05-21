package com.aihub.admin.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.aihub.admin.dto.AdminLoginRequest;
import com.aihub.admin.entity.*;
import com.aihub.admin.mapper.*;
import com.aihub.admin.vo.AdminLoginVO;
import com.aihub.admin.vo.RoleVO;
import com.aihub.admin.vo.UserDetailVO;
import com.aihub.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceImpl - 管理后台服务")
class AdminServiceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysPermissionMapper sysPermissionMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private SysRolePermissionMapper sysRolePermissionMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Nested
    @DisplayName("login - 管理员登录")
    class Login {

        private SysUser createAdmin(String username, String password) {
            SysUser admin = new SysUser();
            admin.setId(1L);
            admin.setUsername(username);
            admin.setPassword(BCrypt.hashpw(password));
            admin.setNickname("Admin");
            admin.setStatus(1);
            return admin;
        }

        @Test
        @DisplayName("账号不存在应抛异常")
        void shouldThrowWhenAccountNotFound() {
            when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AdminLoginRequest req = new AdminLoginRequest();
            req.setUsername("admin");
            req.setPassword("password");

            assertThatThrownBy(() -> adminService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号不存在");
        }

        @Test
        @DisplayName("密码错误应抛异常")
        void shouldThrowWhenWrongPassword() {
            SysUser admin = createAdmin("admin", "correct");
            when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);

            AdminLoginRequest req = new AdminLoginRequest();
            req.setUsername("admin");
            req.setPassword("wrongpassword");

            assertThatThrownBy(() -> adminService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("密码错误");
        }

        @Test
        @DisplayName("登录成功返回 token 和空角色权限列表")
        void shouldReturnTokenWithEmptyRoles() {
            SysUser admin = createAdmin("admin", "admin123");
            when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
            when(sysUserRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            AdminLoginRequest req = new AdminLoginRequest();
            req.setUsername("admin");
            req.setPassword("admin123");

            AdminLoginVO result = adminService.login(req);

            assertThat(result.getUsername()).isEqualTo("admin");
            assertThat(result.getToken()).isNotNull().isNotEmpty();
            assertThat(result.getRoles()).isEmpty();
            assertThat(result.getPermissions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("adjustToken - 调整用户 Token")
    class AdjustToken {

        @Test
        @DisplayName("扣减后余额为负应抛异常")
        void shouldThrowWhenBalanceGoesNegative() {
            com.aihub.admin.entity.User user = new com.aihub.admin.entity.User();
            user.setId(1L);
            user.setTokenBalance(50L);
            when(userMapper.selectById(1L)).thenReturn(user);

            assertThatThrownBy(() -> adminService.adjustToken(1L, -100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("余额不足");
        }

        @Test
        @DisplayName("成功增加 Token")
        void shouldIncreaseToken() {
            com.aihub.admin.entity.User user = new com.aihub.admin.entity.User();
            user.setId(1L);
            user.setTokenBalance(100L);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(com.aihub.admin.entity.User.class))).thenReturn(1);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("aihub:token:balance:1")).thenReturn(null);

            adminService.adjustToken(1L, 50L);

            ArgumentCaptor<com.aihub.admin.entity.User> captor =
                    ArgumentCaptor.forClass(com.aihub.admin.entity.User.class);
            verify(userMapper).updateById(captor.capture());
            assertThat(captor.getValue().getTokenBalance()).isEqualTo(150L);
        }
    }

    @Nested
    @DisplayName("deleteRole - 删除角色")
    class DeleteRole {

        @Test
        @DisplayName("删除角色同时删除关联的权限")
        void shouldDeleteRoleAndCascadePermissions() {
            when(sysRoleMapper.deleteById(1L)).thenReturn(1);
            when(sysRolePermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            adminService.deleteRole(1L);

            verify(sysRoleMapper).deleteById(1L);
            verify(sysRolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("getUserDetail - 用户详情")
    class GetUserDetail {

        @Test
        @DisplayName("用户不存在应抛异常")
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> adminService.getUserDetail(999L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("正常返回含状态描述")
        void shouldReturnWithStatusDesc() {
            com.aihub.admin.entity.User user = new com.aihub.admin.entity.User();
            user.setId(1L);
            user.setNickname("TestUser");
            user.setStatus(1);
            user.setVipLevel(0);
            user.setTokenBalance(100L);
            when(userMapper.selectById(1L)).thenReturn(user);

            UserDetailVO result = adminService.getUserDetail(1L);

            assertThat(result.getStatusDesc()).isEqualTo("正常");
            assertThat(result.getVipLevelDesc()).isEqualTo("免费");
        }
    }
}
