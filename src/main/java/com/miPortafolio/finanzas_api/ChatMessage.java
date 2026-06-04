package com.miPortafolio.finanzas_api;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String chatId;
    private String role; // "user" o "assistant"

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime timestamp;

    public ChatMessage() {}
    public ChatMessage(String chatId, String role, String content) {
        this.chatId = chatId;
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
    public String getRole() { return role; }
    public String getContent() { return content; }
}