package com.scheduler.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.demo.dto.CreateNotificationRequest;
import com.scheduler.demo.model.NotificationJob;
import com.scheduler.demo.repository.NotificationJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class NotificationService {

    private final NotificationJobRepository jobRepo;
    private final TemplateService templateService;
    private final NotificationSender sender;
    private final SqsNotificationService sqsService;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotificationService(NotificationJobRepository jobRepo,
                               TemplateService templateService,
                               NotificationSender sender,
                               SqsNotificationService sqsService) {
        this.jobRepo = jobRepo;
        this.templateService = templateService;
        this.sender = sender;
        this.sqsService = sqsService;
    }

    @Transactional
    public NotificationJob createJob(CreateNotificationRequest req) {
        NotificationJob job = new NotificationJob();
        job.setUserId(req.getUserId());
        job.setType(req.getType());
        job.setTemplateKey(req.getTemplateKey());
        job.setSendAt(req.getSendAt());

        // Set initial status based on whether SQS is enabled
        if (sqsService.isSqsEnabled()) {
            job.setStatus("QUEUED");  // Will be sent to SQS
        } else {
            job.setStatus("PENDING"); // Will be picked up by scheduler
        }

        job.setRecipientEmail(req.getRecipientEmail());
        job.setUserName(req.getUserName());
        try {
            job.setPayload(mapper.writeValueAsString(req.getData()));
        } catch (JsonProcessingException e) {
            job.setPayload("{}");
        }
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        // Save to database first
        job = jobRepo.save(job);

        // If SQS is enabled, send to queue
        if (sqsService.isSqsEnabled()) {
            boolean sent = sqsService.sendToQueue(job);
            if (!sent) {
                log.warn("⚠️  Failed to send job {} to SQS. Will be picked up by scheduler.", job.getId());
                job.setStatus("PENDING"); // Fallback to polling
                job = jobRepo.save(job);
            } else {
                log.info("✅ Job {} sent to SQS successfully", job.getId());
            }
        }

        return job;
    }

    public Optional<NotificationJob> findById(Long id) {
        return jobRepo.findById(id);
    }

    @Transactional
    public boolean cancelJob(Long id) {
        Optional<NotificationJob> opt = jobRepo.findById(id);
        if (opt.isEmpty()) return false;
        NotificationJob j = opt.get();
        if ("PENDING".equals(j.getStatus())) {
            j.setStatus("CANCELLED");
            j.setUpdatedAt(LocalDateTime.now());
            jobRepo.save(j);
            return true;
        }
        return false;
    }

    public void processJob(NotificationJob job) {
        log.info("📧 Processing job ID={}, type={}, template={}", job.getId(), job.getType(), job.getTemplateKey());

        // Step 1: Update status to SENDING (short transaction)
        updateJobStatus(job.getId(), "SENDING");

        // Step 2: Load template and prepare content (no DB connection needed)
        Optional<String> t = templateService.getTemplateContent(job.getTemplateKey());
        String content = t.orElse("Hi, this is notification for template " + job.getTemplateKey());
        log.debug("Template content: {}", content);

        // parse payload JSON to Map
        Map<String, Object> payloadMap = new HashMap<>();
        try {
            payloadMap = mapper.readValue(job.getPayload(), Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse payload JSON: {}", e.getMessage());
        }

        // simple placeholder replacement
        String finalContent = render(content, payloadMap);
        log.debug("Final content after rendering: {}", finalContent);

        // Step 3: Send notification (no DB connection needed - can take 2+ seconds)
        boolean success = false;
        try {
            success = switch (job.getType().toUpperCase(Locale.ROOT)) {
                case "EMAIL" -> sender.sendEmail(job , finalContent);
                case "SMS" -> sender.sendSms(job , finalContent);
                case "PUSH" -> sender.sendPush(job , finalContent);
                default -> sender.sendEmail(job , finalContent);
            };
            log.info("✅ Notification sent successfully for job ID={}", job.getId());
        } catch (Exception e) {
            log.error("❌ Failed to send notification for job ID={}: {}", job.getId(), e.getMessage(), e);
            success = false;
        }

        // Step 4: Update final status (short transaction)
        String finalStatus = success ? "COMPLETED" : "FAILED";
        updateJobStatus(job.getId(), finalStatus);
        log.info("Job ID={} marked as {}", job.getId(), finalStatus);
    }

    @Transactional
    private void updateJobStatus(Long jobId, String status) {
        Optional<NotificationJob> opt = jobRepo.findById(jobId);
        if (opt.isPresent()) {
            NotificationJob job = opt.get();
            job.setStatus(status);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepo.save(job);
        }
    }

    private String render(String template, Map<String, Object> data) {
        String out = template;
        // naive replacement of {{key}} with value
        if (data != null) {
            for (Map.Entry<String, Object> e : data.entrySet()) {
                out = out.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
            }
        }
        return out;
    }

    public List<NotificationJob> findAllByStatus(String status) {
        if (status == null || status.isBlank()) {
            return jobRepo.findAll();
        }
        return jobRepo.findByStatusAndSendAtBefore(status, LocalDateTime.now().plusYears(100)); // trick to reuse method
    }

}

