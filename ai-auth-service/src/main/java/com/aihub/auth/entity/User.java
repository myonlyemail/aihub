package com.aihub.auth.entity;

import com.aihub.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private Long tenantId;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private String password;
    private Integer vipLevel;
    private Long tokenBalance;
    private Integer status;
    private LocalDateTime lastLoginTime;
}
