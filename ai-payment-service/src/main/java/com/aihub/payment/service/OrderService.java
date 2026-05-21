package com.aihub.payment.service;

import com.aihub.payment.dto.CreateOrderRequest;
import com.aihub.payment.dto.PaymentCallback;
import com.aihub.payment.vo.OrderVO;
import com.aihub.payment.vo.ProductVO;

import java.util.List;
import java.util.Map;

public interface OrderService {

    Map<String, String> createOrder(CreateOrderRequest request, Long userId);

    OrderVO getOrder(Long orderId, Long userId);

    OrderVO getOrderByNo(String orderNo);

    List<OrderVO> getUserOrders(Long userId);

    String handleCallback(PaymentCallback callback);

    List<ProductVO> getProducts();
}
