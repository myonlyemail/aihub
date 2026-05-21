package com.aihub.payment.dto;

import lombok.Data;

@Data
public class PaymentCallback {

    private String orderNo;
    private String transactionId;
    private String payType;
    private String status;
    private String sign;
    private String rawData;
}
