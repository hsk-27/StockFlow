package com.stockflow.chat.domain;

import com.stockflow.identity.domain.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "user_role", nullable = false, length = 30)
    private String userRole;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    protected ChatConversation() {
    }

    public ChatConversation(UUID id, User user, String sessionId, String userRole, String title) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.sessionId = sessionId;
        this.userRole = userRole != null ? userRole : "GUEST";
        this.title = title != null ? title : "General Inquiries";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        message.setConversation(this);
        this.updatedAt = Instant.now();
    }

    public void updateTitle(String newTitle) {
        if (newTitle != null && !newTitle.isBlank()) {
            this.title = newTitle.length() > 250 ? newTitle.substring(0, 247) + "..." : newTitle;
            this.updatedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }
}
