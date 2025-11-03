package com.scheduler.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.demo.dto.NotificationMessage;
import com.scheduler.demo.model.NotificationJob;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for sending notification messages to AWS SQS.
 */
@Service
@Slf4j
public class SqsNotificationService {

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue.notification:notification-queue}")
    private String notificationQueueName;

    @Value("${aws.sqs.enabled:false}")
    private boolean sqsEnabled;

    public SqsNotificationService(SqsTemplate sqsTemplate, ObjectMapper objectMapper) {
        this.sqsTemplate = sqsTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a notification job to SQS for processing.
     * 
     * @param job The notification job to send
     * @return true if sent successfully, false otherwise
     */
    public boolean sendToQueue(NotificationJob job) {
        if (!sqsEnabled) {
            log.debug("SQS is disabled, skipping queue send for job {}", job.getId());
            return false;
        }

        if (sqsTemplate == null) {
            log.warn("SQS template is null, cannot send message for job {}", job.getId());
            return false;
        }

        try {
            NotificationMessage message = NotificationMessage.builder()
                    .jobId(job.getId())
                    .userId(job.getUserId())
                    .type(job.getType())
                    .templateKey(job.getTemplateKey())
                    .userName(job.getUserName())
                    .recipientEmail(job.getRecipientEmail())
                    .payload(job.getPayload())
                    .sendAt(job.getSendAt())
                    .retryCount(0)
                    .build();

            // Calculate delay if sendAt is in the future
            Duration delay = calculateDelay(job);

            if (delay != null && delay.getSeconds() > 0) {
                log.info("📤 Sending job {} to SQS with delay of {} seconds", 
                    job.getId(), delay.getSeconds());
                sqsTemplate.send(to -> to
                        .queue(notificationQueueName)
                        .payload(message)
                        .delaySeconds((int) delay.getSeconds())
                );
            } else {
                log.info("📤 Sending job {} to SQS immediately", job.getId());
                sqsTemplate.send(to -> to
                        .queue(notificationQueueName)
                        .payload(message)
                );
            }

            return true;

        } catch (Exception e) {
            log.error("❌ Failed to send job {} to SQS: {}", job.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calculate delay for scheduled messages.
     * SQS supports delays up to 15 minutes (900 seconds).
     * For longer delays, we'll need to use a different approach.
     */
    private Duration calculateDelay(NotificationJob job) {
        if (job.getSendAt() == null) {
            return null;
        }

        Duration delay = Duration.between(java.time.LocalDateTime.now(), job.getSendAt());
        
        if (delay.isNegative() || delay.isZero()) {
            return null; // Send immediately
        }

        // SQS max delay is 15 minutes (900 seconds)
        if (delay.getSeconds() > 900) {
            log.warn("⚠️  Job {} has sendAt > 15 minutes in future. " +
                    "SQS max delay is 15 minutes. Consider using a scheduler for longer delays.", 
                    job.getId());
            return Duration.ofSeconds(900); // Max delay
        }

        return delay;
    }

    /**
     * Check if SQS is enabled and configured.
     */
    public boolean isSqsEnabled() {
        return sqsEnabled && sqsTemplate != null;
    }
}

