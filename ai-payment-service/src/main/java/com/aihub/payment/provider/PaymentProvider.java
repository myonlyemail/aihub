package com.aihub.payment.provider;

import com.aihub.payment.entity.PayOrder;
import java.util.Map;

public interface PaymentProvider {

    String getProviderName();

    Map<String, String> createPayment(PayOrder order);

    boolean verifyCallback(Map<String, String> params, String sign);
}
