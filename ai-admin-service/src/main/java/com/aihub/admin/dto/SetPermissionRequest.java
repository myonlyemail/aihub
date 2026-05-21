package com.aihub.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class SetPermissionRequest {

    private List<String> permissionCodes;
}
