package com.scheduler.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message format for SQS notification queue.
 * This is sent to SQS when a notification job is created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    
    private Long jobId;
    private Long userId;
    private String type;              // EMAIL, SMS, PUSH
    private String templateKey;
    private String userName;
    private String recipientEmail;
    private String payload;           // JSON string

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sendAt;

    // Retry tracking (for manual retry logic if needed)
    private Integer retryCount;
    private String originalMessageId;
}

