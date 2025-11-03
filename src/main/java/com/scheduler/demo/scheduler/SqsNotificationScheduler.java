package com.scheduler.demo.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.demo.dto.NotificationMessage;
import com.scheduler.demo.model.NotificationJob;
import com.scheduler.demo.repository.NotificationJobRepository;
import com.scheduler.demo.service.NotificationService;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Scheduler that polls AWS SQS queue for notification messages.
 * 
 * Flow:
 * 1. POST request → NotificationService saves to DB + pushes to SQS
 * 2. This scheduler polls SQS queue every 10 seconds
 * 3. Processes messages and sends notifications
 * 
 * Only active when aws.sqs.enabled=true
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
public class SqsNotificationScheduler {

    private final NotificationJobRepository jobRepository;
    private final NotificationService notificationService;
    private final Executor notificationExecutor;
    private final SqsTemplate sqsTemplate;
    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue.notification:notification-queue}")
    private String queueName;

    @Value("${app.scheduler.sqs-poll-interval-seconds:10}")
    private int pollIntervalSeconds;

    @Value("${app.scheduler.sqs-max-messages:10}")
    private int maxMessages;

    private String queueUrl;

    public SqsNotificationScheduler(NotificationJobRepository jobRepository,
                                    NotificationService notificationService,
                                    Executor notificationExecutor,
                                    SqsTemplate sqsTemplate,
                                    SqsAsyncClient sqsAsyncClient,
                                    ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.notificationExecutor = notificationExecutor;
        this.sqsTemplate = sqsTemplate;
        this.sqsAsyncClient = sqsAsyncClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Initialize queue URL on first poll
     */
    private void initializeQueueUrl() {
        if (queueUrl == null) {
            try {
                queueUrl = sqsAsyncClient.getQueueUrl(req -> req.queueName(queueName))
                    .join()
                    .queueUrl();
                log.info("✅ [SQS Scheduler] Initialized queue URL: {}", queueUrl);
            } catch (Exception e) {
                log.error("❌ [SQS Scheduler] Failed to get queue URL for {}: {}", queueName, e.getMessage());
                throw new RuntimeException("Failed to initialize SQS queue URL", e);
            }
        }
    }

    /**
     * Poll SQS queue for messages every 10 seconds (configurable).
     */
    @Scheduled(fixedDelayString = "${app.scheduler.sqs-poll-interval-seconds:10}000")
    public void pollSqsQueue() {
        try {
            initializeQueueUrl();

            log.debug("📊 [SQS Scheduler] Polling queue: {} (URL: {})", queueName, queueUrl);

            // Build receive request with proper configuration
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(10)  // Long polling - wait up to 10 seconds for messages
                .visibilityTimeout(30)  // Messages invisible for 30 seconds while processing
                .build();

            // Receive messages using AWS SDK directly
            ReceiveMessageResponse response = sqsAsyncClient.receiveMessage(receiveRequest).join();

            log.info("📊 [SQS Scheduler] Poll result - Messages received: {}, HasMessages: {}",
                response.messages().size(),
                response.hasMessages());

            if (!response.hasMessages() || response.messages().isEmpty()) {
                log.debug("📊 [SQS Scheduler] No messages available in queue");
                return;
            }

            log.info("📨 [SQS Scheduler] Received {} messages from queue", response.messages().size());

            // Process each raw message
            response.messages().forEach(sqsMessage -> {
                try {
                    log.info("📨 [SQS Scheduler] Raw message body: {}", sqsMessage.body());

                    // Parse the message body to NotificationMessage
                    NotificationMessage notification = objectMapper.readValue(
                        sqsMessage.body(),
                        NotificationMessage.class
                    );

                    log.info("✅ [SQS Scheduler] Parsed message - jobId: {}", notification.getJobId());

                    // Process the notification
                    CompletableFuture.runAsync(() -> {
                        try {
                            processNotification(notification);

                            // Delete message from queue after successful processing
                            deleteMessage(sqsMessage.receiptHandle());

                        } catch (Exception e) {
                            log.error("❌ [SQS Scheduler] Failed to process notification: {}", e.getMessage(), e);
                        }
                    }, notificationExecutor);

                } catch (Exception e) {
                    log.error("❌ [SQS Scheduler] Failed to parse message: {}", e.getMessage(), e);
                    log.error("❌ [SQS Scheduler] Message body was: {}", sqsMessage.body());
                }
            });

        } catch (Exception e) {
            log.error("❌ [SQS Scheduler] Error polling SQS: {}", e.getMessage(), e);
        }
    }

    /**
     * Delete a message from the queue after successful processing.
     */
    private void deleteMessage(String receiptHandle) {
        try {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

            sqsAsyncClient.deleteMessage(deleteRequest).join();
            log.info("🗑️  [SQS Scheduler] Deleted message from queue");

        } catch (Exception e) {
            log.error("❌ [SQS Scheduler] Failed to delete message: {}", e.getMessage());
        }
    }

    /**
     * Process a notification message.
     */
    private void processNotification(NotificationMessage notification) {
        try {
            log.info("🔄 [SQS Scheduler] Processing job ID={}", notification.getJobId());

            // Fetch job from database
            Optional<NotificationJob> jobOpt = jobRepository.findById(notification.getJobId());

            if (jobOpt.isEmpty()) {
                log.error("❌ [SQS Scheduler] Job {} not found in database", notification.getJobId());
                return;
            }

            NotificationJob job = jobOpt.get();

            // Check if job is still pending/queued
            if (!"PENDING".equals(job.getStatus()) && !"QUEUED".equals(job.getStatus())) {
                log.warn("⚠️  [SQS Scheduler] Job {} has status {}. Skipping.",
                    job.getId(), job.getStatus());
                return;
            }

            // Process the notification
            notificationService.processJob(job);

            log.info("✅ [SQS Scheduler] Successfully processed job {}", job.getId());

        } catch (Exception e) {
            log.error("❌ [SQS Scheduler] Failed to process job {}: {}",
                notification.getJobId(), e.getMessage(), e);

            // Mark job as failed
            markJobAsFailed(notification.getJobId());

            // Re-throw to let caller handle
            throw new RuntimeException("Failed to process notification: " + e.getMessage(), e);
        }
    }

    /**
     * Mark a job as failed in the database.
     */
    private void markJobAsFailed(Long jobId) {
        try {
            Optional<NotificationJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isPresent()) {
                NotificationJob job = jobOpt.get();
                job.setStatus("FAILED");
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
                log.info("Updated job {} status to FAILED", jobId);
            }
        } catch (Exception ex) {
            log.error("Failed to update job {} status: {}", jobId, ex.getMessage());
        }
    }
}

