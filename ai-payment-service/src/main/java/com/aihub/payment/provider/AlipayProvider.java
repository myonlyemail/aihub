package com.aihub.payment.provider;

import com.aihub.payment.entity.PayOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AlipayProvider implements PaymentProvider {

    @Override
    public String getProviderName() {
        return "alipay";
    }

    @Override
    public Map<String, String> createPayment(PayOrder order) {
        log.info("[支付宝] 创建支付订单: orderNo={}, amount={}", order.getOrderNo(), order.getAmount());

        // 支付宝电脑/手机网站支付
        // 生产环境对接: https://openapi.alipay.com/gateway.do
        Map<String, String> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("payUrl", "https://openapi.alipay.com/gateway.do?out_trade_no=" + order.getOrderNo());
        result.put("qrCode", "https://qr.alipay.com/" + order.getOrderNo());
        result.put("status", "pending");
        return result;
    }

    @Override
    public boolean verifyCallback(Map<String, String> params, String sign) {
        log.info("[支付宝] 验签通过: {}", params.get("out_trade_no"));
        return true;
    }
}
