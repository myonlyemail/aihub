package com.aihub.chat.controller;

import com.aihub.chat.dto.ChatRequest;
import com.aihub.chat.service.ChatService;
import com.aihub.chat.vo.ChatVO;
import com.aihub.chat.vo.MessageVO;
import com.aihub.chat.vo.SessionVO;
import com.aihub.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "AI聊天", description = "多模型智能对话/流式输出/会话管理")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "流式聊天 (SSE)")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(request, userId);
    }

    @Operation(summary = "同步聊天")
    @PostMapping("/send")
    public Result<ChatVO> chatSync(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChatRequest request) {
        request.setStream(false);
        return Result.success(chatService.chatSync(request, userId));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> getSessions(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.success(chatService.getSessions(userId));
    }

    @Operation(summary = "获取会话消息")
    @GetMapping("/messages/{sessionId}")
    public Result<List<MessageVO>> getMessages(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long sessionId) {
        return Result.success(chatService.getMessages(sessionId));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<?> deleteSession(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long sessionId) {
        chatService.deleteSession(sessionId, userId);
        return Result.success();
    }
}
