package com.aihub.admin.service;

import com.aihub.admin.dto.AdminLoginRequest;
import com.aihub.admin.dto.UserQueryRequest;
import com.aihub.admin.vo.AdminLoginVO;
import com.aihub.admin.vo.DashboardVO;
import com.aihub.admin.vo.RoleVO;
import com.aihub.admin.vo.UserDetailVO;
import com.aihub.common.result.PageResult;

import java.util.List;

public interface AdminService {

    AdminLoginVO login(AdminLoginRequest request);

    void logout(Long adminId);

    DashboardVO dashboard();

    PageResult<UserDetailVO> listUsers(UserQueryRequest request);

    UserDetailVO getUserDetail(Long userId);

    void updateUserStatus(Long userId, Integer status);

    void adjustToken(Long userId, Long amount);

    List<RoleVO> listRoles();

    RoleVO createRole(RoleVO vo);

    void updateRole(Long roleId, RoleVO vo);

    void deleteRole(Long roleId);

    List<String> getRolePermissions(Long roleId);

    void setRolePermissions(Long roleId, List<String> permissionCodes);
}
