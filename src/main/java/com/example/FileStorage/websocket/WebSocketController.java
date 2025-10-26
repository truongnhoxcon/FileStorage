package com.example.FileStorage.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
public class WebSocketController {

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public Map<String, Object> handleChatMessage(Map<String, Object> message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "chat");
        response.put("message", message.get("message"));
        response.put("sender", message.get("sender"));
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    @MessageMapping("/file-status")
    @SendTo("/topic/file-status")
    public Map<String, Object> handleFileStatus(Map<String, Object> status) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "file-status");
        response.put("fileName", status.get("fileName"));
        response.put("status", status.get("status"));
        response.put("progress", status.get("progress"));
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    @MessageMapping("/user-typing")
    @SendTo("/topic/typing")
    public Map<String, Object> handleUserTyping(Map<String, Object> typing) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "typing");
        response.put("userId", typing.get("userId"));
        response.put("username", typing.get("username"));
        response.put("isTyping", typing.get("isTyping"));
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    @MessageMapping("/private-message")
    @SendToUser("/queue/private")
    public Map<String, Object> handlePrivateMessage(Map<String, Object> message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "private-message");
        response.put("message", message.get("message"));
        response.put("from", message.get("from"));
        response.put("to", message.get("to"));
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}

