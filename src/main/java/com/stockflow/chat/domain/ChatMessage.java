package com.stockflow.chat.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatSender sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String intent;

    @Column(name = "suggested_actions", columnDefinition = "TEXT")
    private String suggestedActions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(UUID id, ChatConversation conversation, ChatSender sender, String content, String intent, String suggestedActions) {
        this.id = id != null ? id : UUID.randomUUID();
        this.conversation = conversation;
        this.sender = sender;
        this.content = content;
        this.intent = intent;
        this.suggestedActions = suggestedActions;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ChatConversation getConversation() {
        return conversation;
    }

    public void setConversation(ChatConversation conversation) {
        this.conversation = conversation;
    }

    public ChatSender getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public String getIntent() {
        return intent;
    }

    public String getSuggestedActions() {
        return suggestedActions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
