package com.aihub.admin.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoleVO {

    private Long roleId;
    private String roleName;
    private String roleCode;
    private Integer status;
    private List<String> permissions;
}
