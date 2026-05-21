package com.aihub.payment.controller;

import com.aihub.common.result.Result;
import com.aihub.payment.dto.CreateOrderRequest;
import com.aihub.payment.dto.PaymentCallback;
import com.aihub.payment.service.OrderService;
import com.aihub.payment.vo.OrderVO;
import com.aihub.payment.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "支付中心", description = "订单/充值/支付回调")
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orderService;

    @Operation(summary = "获取商品列表")
    @GetMapping("/products")
    public Result<List<ProductVO>> getProducts() {
        return Result.success(orderService.getProducts());
    }

    @Operation(summary = "创建支付订单")
    @PostMapping("/create")
    public Result<Map<String, String>> createOrder(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.createOrder(request, userId));
    }

    @Operation(summary = "查询订单")
    @GetMapping("/order/{orderId}")
    public Result<OrderVO> getOrder(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId) {
        return Result.success(orderService.getOrder(orderId, userId));
    }

    @Operation(summary = "获取用户订单列表")
    @GetMapping("/orders")
    public Result<List<OrderVO>> getOrders(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.success(orderService.getUserOrders(userId));
    }

    @Operation(summary = "支付回调(微信/支付宝)")
    @PostMapping("/callback")
    public String callback(@RequestBody PaymentCallback callback) {
        return orderService.handleCallback(callback);
    }

    @Operation(summary = "查询订单(通过订单号)")
    @GetMapping("/order-by-no/{orderNo}")
    public Result<OrderVO> getOrderByNo(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderByNo(orderNo));
    }
}
