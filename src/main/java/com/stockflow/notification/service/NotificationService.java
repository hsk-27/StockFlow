package com.stockflow.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ============================================================================
 * [MODULE: NOTIFICATION SERVICE]
 * ============================================================================
 * Dispatches transactional notifications (e.g., email / SMS / webhooks) to
 * customers and sellers upon outbox domain events.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // Maintains in-memory audit trail of sent notifications for verification & testing
    private final ConcurrentLinkedQueue<SentNotification> sentNotifications = new ConcurrentLinkedQueue<>();

    public record SentNotification(String recipient, String subject, String body) {}

    public void sendEmail(String recipient, String subject, String body) {
        log.info("[NOTIFICATION DISPATCH] Recipient: {}, Subject: '{}', Body: '{}'", recipient, subject, body);
        sentNotifications.add(new SentNotification(recipient, subject, body));
    }

    public int getSentNotificationCount() {
        return sentNotifications.size();
    }

    public void clear() {
        sentNotifications.clear();
    }
}
