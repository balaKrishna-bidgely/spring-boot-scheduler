package com.scheduler.demo.dto;

import java.time.LocalDateTime;

public class NotificationResponse {
    private Long id;
    private String status;
    private LocalDateTime sendAt;
    private String recipientEmail;
    private String userName;

    public NotificationResponse() {}
    public NotificationResponse(Long id, String status, LocalDateTime sendAt, String recipientEmail, String userName) {
        this.id = id; this.status = status; this.sendAt = sendAt; this.recipientEmail = recipientEmail; this.userName = userName;
    }
    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getSendAt() { return sendAt; }
    public void setSendAt(LocalDateTime sendAt) { this.sendAt = sendAt; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}

