package com.webchat.wchat.config;

import com.webchat.wchat.chat.MessageType;
import com.webchat.wchat.model.ChatMessage;
import com.webchat.wchat.model.User;
import com.webchat.wchat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import jakarta.annotation.PreDestroy;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final UserService userService;
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    @PreDestroy
    public void cleanup() {
        userSessions.clear();
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Principal principal = headerAccessor.getUser();
        String username = principal != null ? principal.getName() : null;

        if (username != null && sessionId != null) {
            userSessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        Long roomId = (Long) headerAccessor.getSessionAttributes().get("room_id");
        if (sessionId != null) {
            userSessions.forEach((user, sessions) -> {
                if (sessions.remove(sessionId) && sessions.isEmpty()) {
                    userSessions.remove(user);
                    if (roomId != null) {
                        sendLeaveMessage(user, roomId);
                    }
                }
            });
        }
    }

    private void sendLeaveMessage(String username, Long roomId) {
        try {
            User user = userService.findByUsername(username);
            var chatMessage = ChatMessage.builder()
                    .type(MessageType.LEAVE)
                    .sender(user)
                    .build();
            messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage);
        } catch (Exception e) {
            log.error("Error sending leave message", e);
        }
    }
}