package com.aihub.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "商品编码不能为空")
    private String productCode;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    @NotBlank(message = "支付方式不能为空")
    private String payType;
}
