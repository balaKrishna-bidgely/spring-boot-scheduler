package com.scheduler.demo.service;

import com.scheduler.demo.model.NotificationJob;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Simulates sending a notification. Replace with real SMTP/Push/SMS integration.
 */
@Component
@Slf4j
public class NotificationSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail; // <-- injected from application.yml

    @Value("${app.email.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.email.rate-limit.delay-ms:2000}")
    private long delayBetweenEmails; // 2 seconds between emails by default

    @Value("${app.email.rate-limit.max-concurrent:3}")
    private int maxConcurrentEmails; // Max 3 emails at a time

    @Value("${app.email.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${app.email.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.email.retry.delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.email.rate-limit.timeout-seconds:300}")
    private int semaphoreTimeoutSeconds; // How long to wait for a permit

    // Semaphore to limit concurrent email sending
    private final Semaphore emailSemaphore;

    public NotificationSender(JavaMailSender mailSender,
                            @Value("${app.email.rate-limit.max-concurrent:3}") int maxConcurrent) {
        this.mailSender = mailSender;
        this.emailSemaphore = new Semaphore(maxConcurrent);
    }

    public boolean sendEmail(NotificationJob job, String content) {
        // simulate sending
        log.info("[SIM-SEND] EMAIL to user={} -> {} | payload={}", job.getUserName(), content, job.getPayload());
        sendEmail(job);
        return true;
    }

    public boolean sendSms(NotificationJob job, String content) {
        log.info("[SIM-SEND] SMS to user={} -> {} | payload={}", job.getUserName(), content, job.getPayload());
        return true;
    }

    public boolean sendPush(NotificationJob job, String content) {
        log.info("[SIM-SEND] PUSH to user={} -> {} | payload={}", job.getUserName(), content, job.getPayload());
        return true;
    }

    private void sendEmail(NotificationJob job) {
        if (!rateLimitEnabled) {
            // Rate limiting disabled - send immediately
            sendEmailInternal(job);
            return;
        }

        // Rate limiting enabled - use semaphore and delay
        try {
            // Wait for a permit (max concurrent emails)
            log.debug("Waiting for email sending permit for job {}... (timeout: {}s)",
                job.getId(), semaphoreTimeoutSeconds);
            boolean acquired = emailSemaphore.tryAcquire(semaphoreTimeoutSeconds, TimeUnit.SECONDS);

            if (!acquired) {
                log.error("❌ Could not acquire email permit within {} seconds for job {}. " +
                         "This means the queue is too long. Consider: " +
                         "1) Increasing max-concurrent, " +
                         "2) Reducing delay-ms, " +
                         "3) Using AWS SQS instead of polling",
                         semaphoreTimeoutSeconds, job.getId());
                throw new RuntimeException("Email rate limit timeout - too many concurrent emails. " +
                    "Waited " + semaphoreTimeoutSeconds + " seconds but no slot available.");
            }

            try {
                // Add delay between emails to avoid rate limiting
                if (delayBetweenEmails > 0) {
                    log.debug("Waiting {}ms before sending email for job {}...", delayBetweenEmails, job.getId());
                    Thread.sleep(delayBetweenEmails);
                }

                // Send the email
                sendEmailInternal(job);

            } finally {
                // Release the permit
                emailSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Email sending interrupted for job {}", job.getId());
            throw new RuntimeException("Email sending interrupted", e);
        }
    }

    private void sendEmailInternal(NotificationJob job) {
        String recipientEmail = job.getRecipientEmail();
        String subject = "Notification from Smart Scheduler";
        String body = String.format("Hi User %s,%n%nSTATUS:%s%n%s%n%n-- Notification Service",
            job.getUserName(), job.getStatus(), job.getPayload());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);

        if (!retryEnabled) {
            // No retry - send once
            sendEmailWithoutRetry(message, recipientEmail, job.getId());
            return;
        }

        // Retry logic
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.debug("Sending email to {} for job {} (attempt {}/{})...",
                    recipientEmail, job.getId(), attempt, maxRetryAttempts);

                mailSender.send(message);
                log.info("✅ Email sent successfully to {} for job {} (attempt {})",
                    recipientEmail, job.getId(), attempt);
                return; // Success!

            } catch (Exception e) {
                lastException = e;

                // Check if it's a temporary error (421 = temporary system problem)
                boolean isTemporaryError = e.getMessage() != null &&
                    (e.getMessage().contains("421") ||
                     e.getMessage().contains("Temporary") ||
                     e.getMessage().contains("Try again"));

                if (attempt < maxRetryAttempts && isTemporaryError) {
                    log.warn("⚠️  Temporary error sending email for job {} (attempt {}): {}. Retrying in {}ms...",
                        job.getId(), attempt, e.getMessage(), retryDelayMs);

                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Email retry interrupted", ie);
                    }
                } else if (!isTemporaryError) {
                    // Permanent error - don't retry
                    log.error("❌ Permanent error sending email for job {}: {}", job.getId(), e.getMessage());
                    throw new RuntimeException("Email sending failed (permanent error)", e);
                } else {
                    // Max retries reached
                    log.error("❌ Failed to send email for job {} after {} attempts: {}",
                        job.getId(), maxRetryAttempts, e.getMessage());
                }
            }
        }

        // All retries failed
        throw new RuntimeException("Email sending failed after " + maxRetryAttempts + " attempts", lastException);
    }

    private void sendEmailWithoutRetry(SimpleMailMessage message, String recipientEmail, Long jobId) {
        try {
            log.debug("Sending email to {} for job {}...", recipientEmail, jobId);
            mailSender.send(message);
            log.info("✅ Email sent successfully to {} for job {}", recipientEmail, jobId);
        } catch (Exception e) {
            log.error("❌ Failed to send email for job {}: {}", jobId, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }
}

