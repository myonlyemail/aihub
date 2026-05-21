package com.aihub.chat.entity;

import com.aihub.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_message")
public class ChatMessage extends BaseEntity {

    private Long sessionId;
    private String role;
    private String content;
    private Integer tokenCost;
}
