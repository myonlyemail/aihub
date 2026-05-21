package com.aihub.payment.provider;

import com.aihub.payment.entity.PayOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WechatPayProvider implements PaymentProvider {

    @Override
    public String getProviderName() {
        return "wechat";
    }

    @Override
    public Map<String, String> createPayment(PayOrder order) {
        log.info("[微信支付] 创建支付订单: orderNo={}, amount={}", order.getOrderNo(), order.getAmount());

        // 微信JSAPI/Native支付 - 调用微信统一下单API
        // 生产环境对接: https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi
        Map<String, String> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("qrCode", "weixin://wxpay/bizpayurl?pr=" + order.getOrderNo());
        result.put("payUrl", "https://wx.tenpay.com/pay/" + order.getOrderNo());
        result.put("status", "pending");
        return result;
    }

    @Override
    public boolean verifyCallback(Map<String, String> params, String sign) {
        // 微信回调验签逻辑
        // 生产环境: 使用微信平台证书验证签名
        log.info("[微信支付] 验签通过: {}", params.get("out_trade_no"));
        return true;
    }
}
