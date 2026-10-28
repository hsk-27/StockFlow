package com.stockflow.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.outbox.domain.OutboxEvent;
import com.stockflow.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes outbox event in the same ACID transaction as the business aggregate change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishEvent(String eventType, String aggregateType, String aggregateId, Object payload) {
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            jsonPayload = "{\"error\": \"Failed to serialize payload\"}";
        }

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId,
                jsonPayload
        );
        return outboxEventRepository.save(event);
    }
}
