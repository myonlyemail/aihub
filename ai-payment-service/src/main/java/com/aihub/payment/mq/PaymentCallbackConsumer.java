package com.aihub.payment.mq;

import com.aihub.payment.dto.PaymentCallback;
import com.aihub.payment.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCallbackConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "ai.billing.queue")
    public void handlePaymentCallback(byte[] message) {
        try {
            PaymentCallback callback = objectMapper.readValue(new String(message), PaymentCallback.class);
            log.info("收到支付回调MQ消息: orderNo={}", callback.getOrderNo());
            orderService.handleCallback(callback);
        } catch (Exception e) {
            log.error("支付回调MQ处理失败", e);
        }
    }
}
