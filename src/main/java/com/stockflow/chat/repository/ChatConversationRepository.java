package com.stockflow.chat.repository;

import com.stockflow.chat.domain.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
    List<ChatConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<ChatConversation> findBySessionIdOrderByUpdatedAtDesc(String sessionId);
    Optional<ChatConversation> findTopBySessionIdOrderByUpdatedAtDesc(String sessionId);
    Optional<ChatConversation> findByIdAndSessionId(UUID id, String sessionId);
}
