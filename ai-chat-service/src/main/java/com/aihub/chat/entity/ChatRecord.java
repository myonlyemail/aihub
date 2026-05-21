package com.aihub.chat.entity;

import com.aihub.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_record")
public class ChatRecord extends BaseEntity {

    private Long userId;
    private Long sessionId;
    private String modelName;
    private String prompt;
    private String completion;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal cost;
}
