package com.aihub.payment.config;

import com.aihub.payment.vo.ProductVO;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Component
public class ProductConfig {

    private final Map<String, ProductVO> products = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        addProduct("token-100", "100 Token包", "100 Token 充值", BigDecimal.valueOf(1.00),
                "token", 100, 0, List.of("即时到账", "永久有效"));
        addProduct("token-500", "500 Token包", "500 Token 充值", BigDecimal.valueOf(5.00),
                "token", 500, 0, List.of("即时到账", "永久有效"));
        addProduct("token-1000", "1000 Token包", "1000 Token 充值", BigDecimal.valueOf(9.90),
                "token", 1000, 0, List.of("折扣优惠", "即时到账", "永久有效"));
        addProduct("token-5000", "5000 Token包", "5000 Token 充值", BigDecimal.valueOf(45.00),
                "token", 5000, 0, List.of("超值优惠", "即时到账", "永久有效"));
        addProduct("token-10000", "10000 Token包", "10000 Token 充值", BigDecimal.valueOf(88.00),
                "token", 10000, 0, List.of("最超值", "即时到账", "永久有效"));

        addProduct("vip-month", "月度VIP", "VIP月度会员", BigDecimal.valueOf(19.90),
                "vip", 1000, 30, List.of("GPT-4/Claude", "高级绘图", "10次/分钟", "优先队列"));
        addProduct("vip-year", "年度VIP", "VIP年度会员", BigDecimal.valueOf(199.00),
                "vip", 12000, 365, List.of("全部VIP权益", "赠送12000 Token", "不限次数"));
        addProduct("svip-year", "SVIP年度", "SVIP年度会员", BigDecimal.valueOf(499.00),
                "svip", 30000, 365, List.of("全部SVIP权益", "视频生成", "API开放", "专属客服"));
    }

    private void addProduct(String code, String name, String desc, BigDecimal price,
                            String type, int tokens, int days, List<String> features) {
        ProductVO vo = new ProductVO();
        vo.setCode(code);
        vo.setName(name);
        vo.setDescription(desc);
        vo.setPrice(price);
        vo.setType(type);
        vo.setTokenAmount(tokens);
        vo.setVipDuration(days);
        vo.setFeatures(features);
        products.put(code, vo);
    }
}
