package com.scheduler.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_jobs", indexes = {
        @Index(name = "idx_status_sendat", columnList = "status, send_at")
})
public class NotificationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(length = 50)
    private String type; // EMAIL, SMS, PUSH

    @Column(length = 100)
    private String templateKey;

    @Column(name = "send_at")
    private LocalDateTime sendAt;

    @Column(length = 20)
    private String status; // PENDING, SENDING, COMPLETED, FAILED, CANCELLED

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters / setters omitted for brevity — include them in production or use IDE to generate
    // For brevity, I'll include full getters/setters below:

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public LocalDateTime getSendAt() { return sendAt; }
    public void setSendAt(LocalDateTime sendAt) { this.sendAt = sendAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}

