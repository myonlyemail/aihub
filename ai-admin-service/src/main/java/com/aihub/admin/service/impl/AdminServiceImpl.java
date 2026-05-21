package com.aihub.admin.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.aihub.admin.dto.AdminLoginRequest;
import com.aihub.admin.dto.UserQueryRequest;
import com.aihub.admin.entity.*;
import com.aihub.admin.mapper.*;
import com.aihub.admin.service.AdminService;
import com.aihub.admin.vo.AdminLoginVO;
import com.aihub.admin.vo.DashboardVO;
import com.aihub.admin.vo.RoleVO;
import com.aihub.admin.vo.UserDetailVO;
import com.aihub.common.exception.BusinessException;
import com.aihub.common.result.PageResult;
import com.aihub.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ADMIN_TOKEN_KEY = "aihub:admin:token:";

    @Override
    public AdminLoginVO login(AdminLoginRequest request) {
        SysUser admin = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));

        if (admin == null) {
            throw BusinessException.badRequest("账号不存在");
        }
        if (admin.getStatus() == 0) {
            throw BusinessException.badRequest("账号已被禁用");
        }
        if (!BCrypt.checkpw(request.getPassword(), admin.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }

        String token = JwtUtil.createToken(admin.getId());
        redisTemplate.opsForValue().set(ADMIN_TOKEN_KEY + admin.getId(), token, 30, TimeUnit.DAYS);

        AdminLoginVO vo = new AdminLoginVO();
        vo.setAdminId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setNickname(admin.getNickname());
        vo.setToken(token);

        // 查询用户角色和权限
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, admin.getId()));
        if (!userRoles.isEmpty()) {
            List<String> roleCodes = new ArrayList<>();
            List<String> permCodes = new ArrayList<>();
            for (SysUserRole ur : userRoles) {
                SysRole role = sysRoleMapper.selectById(ur.getRoleId());
                if (role != null && role.getStatus() == 1) {
                    roleCodes.add(role.getRoleCode());
                    List<SysRolePermission> rps = sysRolePermissionMapper.selectList(
                            new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, role.getId()));
                    for (SysRolePermission rp : rps) {
                        SysPermission perm = sysPermissionMapper.selectById(rp.getPermissionId());
                        if (perm != null) {
                            permCodes.add(perm.getPermissionCode());
                        }
                    }
                }
            }
            vo.setRoles(roleCodes);
            vo.setPermissions(permCodes);
        } else {
            vo.setRoles(Collections.emptyList());
            vo.setPermissions(Collections.emptyList());
        }

        return vo;
    }

    @Override
    public void logout(Long adminId) {
        redisTemplate.delete(ADMIN_TOKEN_KEY + adminId);
    }

    @Override
    public DashboardVO dashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setTotalUsers(userMapper.countTotalUsers());
        vo.setTodayNewUsers(userMapper.countTodayNewUsers());
        vo.setTodayRevenue(userMapper.todayRevenue());
        vo.setTotalRevenue(userMapper.totalRevenue());
        vo.setTodayTokens(userMapper.todayTokens());
        vo.setTotalTokens(userMapper.totalTokens());
        vo.setTodayChatCount(userMapper.countTodayChat());
        vo.setTodayImageCount(userMapper.countTodayImage());
        vo.setTodayVideoCount(userMapper.countTodayVideo());
        vo.setActiveUsers(userMapper.countActiveUsers());
        vo.setVipUsers(userMapper.countVipUsers());
        return vo;
    }

    @Override
    public PageResult<UserDetailVO> listUsers(UserQueryRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.isNotBlank(request.getKeyword()), User::getMobile, request.getKeyword())
                .or()
                .like(StringUtils.isNotBlank(request.getKeyword()), User::getNickname, request.getKeyword())
                .eq(request.getVipLevel() != null, User::getVipLevel, request.getVipLevel())
                .eq(request.getStatus() != null, User::getStatus, request.getStatus())
                .orderByDesc(User::getCreateTime);

        Page<User> page = new Page<>(request.getPage(), request.getSize());
        Page<User> result = userMapper.selectPage(page, wrapper);

        List<UserDetailVO> records = result.getRecords().stream().map(u -> {
            UserDetailVO vo = new UserDetailVO();
            vo.setUserId(u.getId());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setMobile(u.getMobile());
            vo.setEmail(u.getEmail());
            vo.setVipLevel(u.getVipLevel());
            vo.setVipLevelDesc(getVipDesc(u.getVipLevel()));
            vo.setTokenBalance(u.getTokenBalance());
            vo.setStatus(u.getStatus());
            vo.setStatusDesc(u.getStatus() == 1 ? "正常" : "禁用");
            vo.setLastLoginTime(u.getLastLoginTime());
            vo.setCreateTime(u.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public UserDetailVO getUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }

        UserDetailVO vo = new UserDetailVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setVipLevel(user.getVipLevel());
        vo.setVipLevelDesc(getVipDesc(user.getVipLevel()));
        vo.setTokenBalance(user.getTokenBalance());
        vo.setStatus(user.getStatus());
        vo.setStatusDesc(user.getStatus() == 1 ? "正常" : "禁用");
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void adjustToken(Long userId, Long amount) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        long newBalance = user.getTokenBalance() + amount;
        if (newBalance < 0) {
            throw BusinessException.badRequest("Token余额不足，无法扣减");
        }
        user.setTokenBalance(newBalance);
        userMapper.updateById(user);

        String balanceKey = "aihub:token:balance:" + userId;
        Object cached = redisTemplate.opsForValue().get(balanceKey);
        if (cached != null) {
            redisTemplate.opsForValue().increment(balanceKey, amount);
        }
    }

    @Override
    public List<RoleVO> listRoles() {
        List<SysRole> roles = sysRoleMapper.selectList(null);
        return roles.stream().map(r -> {
            RoleVO vo = new RoleVO();
            vo.setRoleId(r.getId());
            vo.setRoleName(r.getRoleName());
            vo.setRoleCode(r.getRoleCode());
            vo.setStatus(r.getStatus());

            List<SysRolePermission> rps = sysRolePermissionMapper.selectList(
                    new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, r.getId()));
            if (!rps.isEmpty()) {
                List<Long> permIds = rps.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
                List<SysPermission> perms = sysPermissionMapper.selectBatchIds(permIds);
                vo.setPermissions(perms.stream().map(SysPermission::getPermissionCode).collect(Collectors.toList()));
            } else {
                vo.setPermissions(Collections.emptyList());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoleVO createRole(RoleVO vo) {
        SysRole role = new SysRole();
        role.setRoleName(vo.getRoleName());
        role.setRoleCode(vo.getRoleCode());
        role.setStatus(vo.getStatus() != null ? vo.getStatus() : 1);
        sysRoleMapper.insert(role);

        vo.setRoleId(role.getId());
        vo.setPermissions(Collections.emptyList());
        return vo;
    }

    @Override
    @Transactional
    public void updateRole(Long roleId, RoleVO vo) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound();
        }
        if (vo.getRoleName() != null) {
            role.setRoleName(vo.getRoleName());
        }
        if (vo.getRoleCode() != null) {
            role.setRoleCode(vo.getRoleCode());
        }
        if (vo.getStatus() != null) {
            role.setStatus(vo.getStatus());
        }
        sysRoleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        sysRoleMapper.deleteById(roleId);
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
    }

    @Override
    public List<String> getRolePermissions(Long roleId) {
        List<SysRolePermission> rps = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        if (rps.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> permIds = rps.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
        return sysPermissionMapper.selectBatchIds(permIds).stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void setRolePermissions(Long roleId, List<String> permissionCodes) {
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));

        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }

        List<SysPermission> perms = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().in(SysPermission::getPermissionCode, permissionCodes));

        for (SysPermission perm : perms) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(perm.getId());
            sysRolePermissionMapper.insert(rp);
        }
    }

    private String getVipDesc(Integer vipLevel) {
        if (vipLevel == null) return "免费";
        return switch (vipLevel) {
            case 1 -> "VIP";
            case 2 -> "SVIP";
            case 9 -> "企业版";
            default -> "免费";
        };
    }
}
