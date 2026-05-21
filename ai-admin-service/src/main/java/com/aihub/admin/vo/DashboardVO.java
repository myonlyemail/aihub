package com.aihub.admin.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardVO {

    private Long totalUsers;
    private Long todayNewUsers;
    private BigDecimal todayRevenue;
    private BigDecimal totalRevenue;
    private Long todayTokens;
    private Long totalTokens;
    private Long todayChatCount;
    private Long todayImageCount;
    private Long todayVideoCount;
    private Long activeUsers;
    private Long vipUsers;
}
