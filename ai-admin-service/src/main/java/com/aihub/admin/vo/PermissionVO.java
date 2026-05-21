package com.aihub.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PermissionVO {

    private Long id;
    private Long parentId;
    private String permissionName;
    private String permissionCode;
    private String permissionType;
    private String path;
    private String icon;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private List<PermissionVO> children;
}
