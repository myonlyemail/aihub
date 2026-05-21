package com.aihub.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {

    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    private String payType;
    private Integer payStatus;
    private String payStatusDesc;
    private String productCode;
    private String subject;
    private LocalDateTime createTime;
}
