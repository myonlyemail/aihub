package com.aihub.chat.service;

import com.aihub.chat.dto.ChatRequest;
import com.aihub.chat.vo.ChatVO;
import com.aihub.chat.vo.MessageVO;
import com.aihub.chat.vo.SessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {

    SseEmitter chatStream(ChatRequest request, Long userId);

    ChatVO chatSync(ChatRequest request, Long userId);

    List<SessionVO> getSessions(Long userId);

    List<MessageVO> getMessages(Long sessionId);

    void deleteSession(Long sessionId, Long userId);
}
