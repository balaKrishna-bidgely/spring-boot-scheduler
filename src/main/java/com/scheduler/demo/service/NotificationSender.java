package com.scheduler.demo.service;

import com.scheduler.demo.model.NotificationJob;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Simulates sending a notification. Replace with real SMTP/Push/SMS integration.
 */
@Component
@Slf4j
public class NotificationSender {

    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String senderEmail; // <-- injected from application.yml

    public NotificationSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
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
        try {
            String recipientEmail = job.getRecipientEmail(); // add this field in entity if not present
            String subject = "Notification from Smart Scheduler";
            String body = String.format("Hi User %s,%n%nSTATUS:%s%n%s%n%n-- Notification Service", job.getUserName(), job.getStatus(), job.getPayload());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);  // same as configured username
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("✅ Email sent successfully to {}", recipientEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send email for job {}: {}", job.getId(), e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }
}

