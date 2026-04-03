package com.college.ara.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.college.ara.service.GeminiChatService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GeminiChatService geminiChatService;

    public ChatController(GeminiChatService geminiChatService) {
        this.geminiChatService = geminiChatService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required."));
        }

        try {
            String reply = geminiChatService.generateReply(request.getMessage().trim());
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    public static class ChatRequest {

        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
