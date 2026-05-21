package com.aihub.payment.entity;

import com.aihub.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_order")
public class PayOrder extends BaseEntity {

    private Long userId;
    private String orderNo;
    private BigDecimal amount;
    private Integer payStatus;
    private String payType;
    private String productCode;
    private String subject;
    private String body;
}
