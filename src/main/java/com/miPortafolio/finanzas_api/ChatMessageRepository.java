package com.miPortafolio.finanzas_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop15ByChatIdOrderByTimestampAsc(String chatId);
}