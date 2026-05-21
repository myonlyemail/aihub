package com.aihub.payment.service.impl;

import com.aihub.common.exception.BusinessException;
import com.aihub.payment.config.ProductConfig;
import com.aihub.payment.dto.CreateOrderRequest;
import com.aihub.payment.dto.PaymentCallback;
import com.aihub.payment.entity.PayOrder;
import com.aihub.payment.mapper.PayOrderMapper;
import com.aihub.payment.provider.AlipayProvider;
import com.aihub.payment.provider.PaymentProvider;
import com.aihub.payment.provider.WechatPayProvider;
import com.aihub.payment.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl - 订单服务")
class OrderServiceImplTest {

    @Mock private PayOrderMapper orderMapper;
    @Mock private ProductConfig productConfig;
    @Mock private WechatPayProvider wechatPayProvider;
    @Mock private AlipayProvider alipayProvider;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    @DisplayName("createOrder - 创建订单")
    class CreateOrder {

        @Test
        @DisplayName("商品不存在应抛异常")
        void shouldThrowWhenProductNotFound() {
            when(productConfig.getProducts()).thenReturn(Map.of());

            CreateOrderRequest req = new CreateOrderRequest();
            req.setProductCode("nonexistent");
            req.setPayType("wechat");

            assertThatThrownBy(() -> orderService.createOrder(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("商品不存在");
        }

        @Test
        @DisplayName("不支持的支付方式应抛异常")
        void shouldThrowForUnsupportedPayType() {
            ProductVO product = new ProductVO();
            product.setCode("tokens_100");
            product.setName("100 Tokens");
            product.setPrice(new BigDecimal("9.90"));
            product.setType("token");
            product.setTokenAmount(100);
            when(productConfig.getProducts()).thenReturn(Map.of("tokens_100", product));

            CreateOrderRequest req = new CreateOrderRequest();
            req.setProductCode("tokens_100");
            req.setPayType("paypal"); // unsupported

            assertThatThrownBy(() -> orderService.createOrder(req, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("支付方式");
        }

        @Test
        @DisplayName("微信支付下单成功返回支付链接")
        void shouldCreateWechatOrder() {
            ProductVO product = new ProductVO();
            product.setCode("tokens_100");
            product.setName("100 Tokens");
            product.setPrice(new BigDecimal("9.90"));
            product.setType("token");
            product.setTokenAmount(100);
            when(productConfig.getProducts()).thenReturn(Map.of("tokens_100", product));
            when(orderMapper.insert(any(PayOrder.class))).thenAnswer(inv -> {
                PayOrder o = inv.getArgument(0);
                o.setId(1L);
                return 1;
            });
            when(wechatPayProvider.createPayment(any(PayOrder.class)))
                    .thenReturn(new java.util.HashMap<>(Map.of("payUrl", "https://pay.weixin.qq.com/xxx")));

            CreateOrderRequest req = new CreateOrderRequest();
            req.setProductCode("tokens_100");
            req.setPayType("wechat");

            Map<String, String> result = orderService.createOrder(req, 1L);

            assertThat(result).containsKeys("orderNo", "payUrl");
            assertThat(result.get("orderNo")).startsWith("AIH");
        }
    }

    @Nested
    @DisplayName("handleCallback - 支付回调")
    class HandleCallback {

        @Test
        @DisplayName("订单不存在返回 fail")
        void shouldReturnFailWhenOrderNotFound() throws InterruptedException {
            PaymentCallback cb = new PaymentCallback();
            cb.setOrderNo("AIH20240521000000001");
            cb.setPayType("wechat");

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThat(orderService.handleCallback(cb)).isEqualTo("fail");
        }

        @Test
        @DisplayName("重复支付回调返回 success（幂等）")
        void shouldReturnSuccessForDuplicateCallback() throws InterruptedException {
            PaymentCallback cb = new PaymentCallback();
            cb.setOrderNo("AIH20240521000000001");
            cb.setPayType("wechat");

            PayOrder order = new PayOrder();
            order.setId(1L);
            order.setPayStatus(1); // already paid
            order.setUserId(1L);
            order.setAmount(new BigDecimal("9.90"));
            order.setProductCode("tokens_100");

            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

            assertThat(orderService.handleCallback(cb)).isEqualTo("success");
            // should NOT call verifyCallback or update order
            verify(orderMapper, never()).updateById(any());
        }
    }
}
