package com.aihub.payment.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.aihub.common.constant.RedisKey;
import com.aihub.common.exception.BusinessException;
import com.aihub.common.service.TokenService;
import com.aihub.payment.config.ProductConfig;
import com.aihub.payment.dto.CreateOrderRequest;
import com.aihub.payment.dto.PaymentCallback;
import com.aihub.payment.entity.PayOrder;
import com.aihub.payment.mapper.PayOrderMapper;
import com.aihub.payment.provider.AlipayProvider;
import com.aihub.payment.provider.PaymentProvider;
import com.aihub.payment.provider.WechatPayProvider;
import com.aihub.payment.service.OrderService;
import com.aihub.payment.vo.OrderVO;
import com.aihub.payment.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final PayOrderMapper orderMapper;
    private final ProductConfig productConfig;
    private final WechatPayProvider wechatPayProvider;
    private final AlipayProvider alipayProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    @Override
    @Transactional
    public Map<String, String> createOrder(CreateOrderRequest request, Long userId) {
        ProductVO product = productConfig.getProducts().get(request.getProductCode());
        if (product == null) {
            throw BusinessException.badRequest("商品不存在");
        }

        PayOrder order = new PayOrder();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setAmount(product.getPrice());
        order.setPayType(request.getPayType());
        order.setPayStatus(0);
        order.setProductCode(product.getCode());
        order.setSubject(product.getName());
        order.setBody(product.getDescription());
        orderMapper.insert(order);

        PaymentProvider provider = getProvider(request.getPayType());
        Map<String, String> paymentResult = provider.createPayment(order);

        String lockKey = "aihub:pay:order:" + order.getOrderNo();
        redisTemplate.opsForValue().set(lockKey, order.getId().toString(), 30, TimeUnit.MINUTES);

        paymentResult.put("orderNo", order.getOrderNo());
        paymentResult.put("orderId", order.getId().toString());
        log.info("订单创建成功: userId={}, orderNo={}, amount={}", userId, order.getOrderNo(), order.getAmount());
        return paymentResult;
    }

    @Override
    public OrderVO getOrder(Long orderId, Long userId) {
        PayOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BusinessException.notFound();
        }
        return toVO(order);
    }

    @Override
    public OrderVO getOrderByNo(String orderNo) {
        PayOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw BusinessException.notFound();
        }
        return toVO(order);
    }

    @Override
    public List<OrderVO> getUserOrders(Long userId) {
        List<PayOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getUserId, userId)
                        .orderByDesc(PayOrder::getCreateTime));
        return orders.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String handleCallback(PaymentCallback callback) {
        String lockKey = "aihub:pay:order:" + callback.getOrderNo();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                log.warn("获取支付回调锁失败: {}", callback.getOrderNo());
                return "fail";
            }

            PayOrder order = orderMapper.selectOne(
                    new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, callback.getOrderNo()));
            if (order == null) {
                log.error("支付回调订单不存在: {}", callback.getOrderNo());
                return "fail";
            }
            if (order.getPayStatus() == 1) {
                log.info("订单已支付,忽略重复回调: {}", callback.getOrderNo());
                return "success";
            }

            PaymentProvider provider = getProvider(callback.getPayType());
            if (!provider.verifyCallback(Map.of("out_trade_no", callback.getOrderNo()), callback.getSign())) {
                log.error("支付回调验签失败: {}", callback.getOrderNo());
                return "fail";
            }

            order.setPayStatus(1);
            orderMapper.updateById(order);

            processRecharge(order);

            log.info("支付回调处理成功: orderNo={}, userId={}, amount={}", order.getOrderNo(), order.getUserId(), order.getAmount());
            return "success";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "fail";
        } catch (Exception e) {
            log.error("支付回调处理异常: {}", callback.getOrderNo(), e);
            return "fail";
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public List<ProductVO> getProducts() {
        return new ArrayList<>(productConfig.getProducts().values());
    }

    private void processRecharge(PayOrder order) {
        ProductVO product = productConfig.getProducts().get(order.getProductCode());
        if (product == null) return;

        String balanceKey = RedisKey.tokenBalance(order.getUserId());

        switch (product.getType()) {
            case "token" -> {
                redisTemplate.opsForValue().increment(balanceKey, product.getTokenAmount());
                log.info("Token充值成功: userId={}, tokens={}", order.getUserId(), product.getTokenAmount());
            }
            case "vip", "svip" -> {
                int vipLevel = "svip".equals(product.getType()) ? 2 : 1;
                redisTemplate.opsForHash().put("aihub:user:vip:" + order.getUserId(), "level", vipLevel);
                redisTemplate.opsForHash().put("aihub:user:vip:" + order.getUserId(), "expire",
                        DateUtil.format(DateUtil.offsetDay(DateUtil.date(), product.getVipDuration()), "yyyy-MM-dd HH:mm:ss"));
                if (product.getTokenAmount() > 0) {
                    redisTemplate.opsForValue().increment(balanceKey, product.getTokenAmount());
                }
                log.info("VIP充值成功: userId={}, vipLevel={}, duration={}天", order.getUserId(), vipLevel, product.getVipDuration());
            }
        }
    }

    private PaymentProvider getProvider(String payType) {
        return switch (payType.toLowerCase()) {
            case "wechat", "wxpay" -> wechatPayProvider;
            case "alipay", "zhifubao" -> alipayProvider;
            default -> throw BusinessException.badRequest("不支持的支付方式: " + payType);
        };
    }

    private String generateOrderNo() {
        return "AIH" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss") + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    private OrderVO toVO(PayOrder order) {
        OrderVO vo = new OrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setAmount(order.getAmount());
        vo.setPayType(order.getPayType());
        vo.setPayStatus(order.getPayStatus());
        vo.setPayStatusDesc(getStatusDesc(order.getPayStatus()));
        vo.setProductCode(order.getProductCode());
        vo.setSubject(order.getSubject());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    private String getStatusDesc(Integer status) {
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已退款";
            default -> "未知";
        };
    }
}
