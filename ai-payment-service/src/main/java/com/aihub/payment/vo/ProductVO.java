package com.aihub.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductVO {

    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String type;
    private Integer tokenAmount;
    private Integer vipDuration;
    private List<String> features;
}
