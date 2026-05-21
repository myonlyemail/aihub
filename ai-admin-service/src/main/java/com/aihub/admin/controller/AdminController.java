package com.aihub.admin.controller;

import com.aihub.admin.dto.*;
import com.aihub.admin.entity.SysPermission;
import com.aihub.admin.mapper.SysPermissionMapper;
import com.aihub.admin.service.AdminService;
import com.aihub.admin.vo.*;
import com.aihub.common.result.PageResult;
import com.aihub.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台", description = "管理员登录/仪表盘/用户管理/角色管理/权限管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SysPermissionMapper sysPermissionMapper;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminService.login(request));
    }

    @Operation(summary = "管理员登出")
    @PostMapping("/logout")
    public Result<?> logout(@RequestHeader("X-User-Id") Long adminId) {
        adminService.logout(adminId);
        return Result.success();
    }

    @Operation(summary = "仪表盘统计")
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.success(adminService.dashboard());
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<UserDetailVO>> listUsers(UserQueryRequest request) {
        return Result.success(adminService.listUsers(request));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/users/{id}")
    public Result<UserDetailVO> getUserDetail(@PathVariable Long id) {
        return Result.success(adminService.getUserDetail(id));
    }

    @Operation(summary = "更新用户状态")
    @PutMapping("/users/{id}/status")
    public Result<?> updateUserStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        adminService.updateUserStatus(id, request.getStatus());
        return Result.success();
    }

    @Operation(summary = "调整用户Token")
    @PutMapping("/users/{id}/token")
    public Result<?> adjustToken(@PathVariable Long id, @RequestBody AdjustTokenRequest request) {
        adminService.adjustToken(id, request.getAmount());
        return Result.success();
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<RoleVO>> listRoles() {
        return Result.success(adminService.listRoles());
    }

    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public Result<RoleVO> createRole(@RequestBody RoleVO vo) {
        return Result.success(adminService.createRole(vo));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{id}")
    public Result<?> updateRole(@PathVariable Long id, @RequestBody RoleVO vo) {
        adminService.updateRole(id, vo);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{id}")
    public Result<?> deleteRole(@PathVariable Long id) {
        adminService.deleteRole(id);
        return Result.success();
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/roles/{id}/permissions")
    public Result<List<String>> getRolePermissions(@PathVariable Long id) {
        return Result.success(adminService.getRolePermissions(id));
    }

    @Operation(summary = "设置角色权限")
    @PutMapping("/roles/{id}/permissions")
    public Result<?> setRolePermissions(@PathVariable Long id, @RequestBody SetPermissionRequest request) {
        adminService.setRolePermissions(id, request.getPermissionCodes());
        return Result.success();
    }

    @Operation(summary = "权限树")
    @GetMapping("/permissions")
    public Result<List<PermissionVO>> listPermissions() {
        List<SysPermission> all = sysPermissionMapper.selectList(null);

        Map<Long, PermissionVO> nodeMap = new HashMap<>();
        for (SysPermission p : all) {
            PermissionVO vo = new PermissionVO();
            vo.setId(p.getId());
            vo.setParentId(p.getParentId());
            vo.setPermissionName(p.getPermissionName());
            vo.setPermissionCode(p.getPermissionCode());
            vo.setPermissionType(p.getPermissionType());
            vo.setPath(p.getPath());
            vo.setIcon(p.getIcon());
            vo.setSortOrder(p.getSortOrder());
            vo.setCreateTime(p.getCreateTime());
            vo.setChildren(new ArrayList<>());
            nodeMap.put(p.getId(), vo);
        }

        List<PermissionVO> tree = new ArrayList<>();
        for (SysPermission p : all) {
            PermissionVO vo = nodeMap.get(p.getId());
            if (p.getParentId() == null || p.getParentId() == 0) {
                tree.add(vo);
            } else {
                PermissionVO parent = nodeMap.get(p.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                }
            }
        }

        return Result.success(tree);
    }
}
