package com.aihub.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aihub.chat.dto.ChatRequest;
import com.aihub.chat.dto.ProviderRequest;
import com.aihub.chat.dto.ProviderResponse;
import com.aihub.chat.entity.ChatMessage;
import com.aihub.chat.entity.ChatRecord;
import com.aihub.chat.entity.ChatSession;
import com.aihub.chat.mapper.ChatMessageMapper;
import com.aihub.chat.mapper.ChatRecordMapper;
import com.aihub.chat.mapper.ChatSessionMapper;
import com.aihub.chat.provider.AiProvider;
import com.aihub.chat.provider.ProviderRouter;
import com.aihub.chat.service.ChatService;
import com.aihub.chat.vo.ChatVO;
import com.aihub.chat.vo.MessageVO;
import com.aihub.chat.vo.SessionVO;
import com.aihub.common.constant.RedisKey;
import com.aihub.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ProviderRouter providerRouter;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatRecordMapper chatRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int TOKEN_COST_PER_CALL = 10;
    private static final int MAX_CONTEXT_MESSAGES = 20;

    @Override
    public SseEmitter chatStream(ChatRequest request, Long userId) {
        Long sessionId = getOrCreateSession(userId, request);
        Long tokenBalance = preConsumeToken(userId, TOKEN_COST_PER_CALL);

        AiProvider provider = providerRouter.route(request.getModelName());
        ProviderRequest providerReq = buildProviderRequest(request, sessionId);

        SseEmitter emitter = new SseEmitter(600_000L);

        CompletableFuture.runAsync(() -> {
            StringBuilder fullContent = new StringBuilder();
            try {
                provider.chatStream(providerReq,
                        chunk -> {
                            try {
                                fullContent.append(chunk);
                                emitter.send(SseEmitter.event().name("message").data(chunk));
                            } catch (IOException e) {
                                log.error("SSE发送失败", e);
                            }
                        },
                        result -> {
                            try {
                                saveChatResult(userId, sessionId, request, result.getContent(),
                                        result.getPromptTokens(), result.getCompletionTokens(), request.getModelName());
                                emitter.send(SseEmitter.event().name("done")
                                        .data("{\"sessionId\":" + sessionId + ",\"tokenCost\":" + TOKEN_COST_PER_CALL + "}"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            refundToken(userId, TOKEN_COST_PER_CALL);
                            emitter.completeWithError(error);
                        }
                );
            } catch (Exception e) {
                refundToken(userId, TOKEN_COST_PER_CALL);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.info("SSE完成: userId={}, sessionId={}", userId, sessionId));
        emitter.onTimeout(() -> {
            log.warn("SSE超时: userId={}, sessionId={}", userId, sessionId);
        });

        return emitter;
    }

    @Override
    @Transactional
    public ChatVO chatSync(ChatRequest request, Long userId) {
        Long sessionId = getOrCreateSession(userId, request);
        preConsumeToken(userId, TOKEN_COST_PER_CALL);

        AiProvider provider = providerRouter.route(request.getModelName());
        ProviderRequest providerReq = buildProviderRequest(request, sessionId);

        ProviderResponse result = provider.chat(providerReq);
        if (!result.isSuccess()) {
            refundToken(userId, TOKEN_COST_PER_CALL);
            throw BusinessException.of("AI调用失败: " + result.getError());
        }

        saveChatResult(userId, sessionId, request, result.getContent(),
                result.getPromptTokens(), result.getCompletionTokens(), request.getModelName());

        ChatVO vo = new ChatVO();
        vo.setSessionId(sessionId);
        vo.setMessage(result.getContent());
        vo.setModelName(request.getModelName());
        vo.setTokenCost(TOKEN_COST_PER_CALL);
        vo.setTokenBalance(getTokenBalance(userId));
        return vo;
    }

    @Override
    public List<SessionVO> getSessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdateTime));

        return sessions.stream().map(s -> {
            SessionVO vo = new SessionVO();
            vo.setSessionId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setModel(s.getModel());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());

            ChatMessage lastMsg = messageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, s.getId())
                            .orderByDesc(ChatMessage::getCreateTime)
                            .last("LIMIT 1"));
            if (lastMsg != null) {
                vo.setLastMessage(StrUtil.sub(lastMsg.getContent(), 0, 100));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<MessageVO> getMessages(Long sessionId) {
        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime));

        return messages.stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setTokenCost(m.getTokenCost());
            vo.setCreateTime(m.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.forbidden();
        }
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
    }

    // ======== 私有方法 ========

    private Long getOrCreateSession(Long userId, ChatRequest request) {
        if (request.getSessionId() != null) {
            ChatSession exist = sessionMapper.selectById(request.getSessionId());
            if (exist != null && exist.getUserId().equals(userId)) {
                return exist.getId();
            }
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(StrUtil.sub(request.getMessage(), 0, 50));
        session.setModel(request.getModelName());
        sessionMapper.insert(session);
        return session.getId();
    }

    private ProviderRequest buildProviderRequest(ChatRequest request, Long sessionId) {
        List<ProviderRequest.Message> messages = new ArrayList<>();

        List<ChatMessage> history = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT " + MAX_CONTEXT_MESSAGES));
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            messages.add(ProviderRequest.Message.builder()
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
        }

        if (request.getHistory() != null) {
            for (ChatRequest.HistoryMessage h : request.getHistory()) {
                messages.add(ProviderRequest.Message.builder()
                        .role(h.getRole()).content(h.getContent()).build());
            }
        }

        messages.add(ProviderRequest.Message.builder()
                .role("user").content(request.getMessage()).build());

        return ProviderRequest.builder()
                .model(request.getModelName())
                .messages(messages)
                .stream(request.getStream())
                .build();
    }

    private void saveChatResult(Long userId, Long sessionId, ChatRequest request,
                                 String completion, int promptTokens, int completionTokens, String model) {

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        userMsg.setTokenCost(promptTokens);
        messageMapper.insert(userMsg);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(completion);
        aiMsg.setTokenCost(completionTokens);
        messageMapper.insert(aiMsg);

        ChatRecord record = new ChatRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setModelName(model);
        record.setPrompt(request.getMessage());
        record.setCompletion(completion);
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);

        BigDecimal cost = BigDecimal.valueOf(promptTokens + completionTokens)
                .multiply(BigDecimal.valueOf(0.0001))
                .setScale(4, RoundingMode.HALF_UP);
        record.setCost(cost);
        chatRecordMapper.insert(record);
    }

    private Long preConsumeToken(Long userId, int amount) {
        String key = RedisKey.tokenBalance(userId);
        Long balance = redisTemplate.opsForValue().decrement(key, amount);
        if (balance != null && balance < 0) {
            redisTemplate.opsForValue().increment(key, amount);
            throw BusinessException.badRequest("Token余额不足，请充值");
        }
        return balance != null ? balance : 0;
    }

    private void refundToken(Long userId, int amount) {
        redisTemplate.opsForValue().increment(RedisKey.tokenBalance(userId), amount);
    }

    private Long getTokenBalance(Long userId) {
        Object val = redisTemplate.opsForValue().get(RedisKey.tokenBalance(userId));
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0L;
    }
}
