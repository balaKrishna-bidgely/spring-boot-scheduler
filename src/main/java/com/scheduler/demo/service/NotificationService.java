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
    private final ObjectMapper mapper = new ObjectMapper();

    public NotificationService(NotificationJobRepository jobRepo,
                               TemplateService templateService,
                               NotificationSender sender) {
        this.jobRepo = jobRepo;
        this.templateService = templateService;
        this.sender = sender;
    }

    @Transactional
    public NotificationJob createJob(CreateNotificationRequest req) {
        NotificationJob job = new NotificationJob();
        job.setUserId(req.getUserId());
        job.setType(req.getType());
        job.setTemplateKey(req.getTemplateKey());
        job.setSendAt(req.getSendAt());
        job.setStatus("PENDING");
        job.setRecipientEmail(req.getRecipientEmail());
        job.setUserName(req.getUserName());
        try {
            job.setPayload(mapper.writeValueAsString(req.getData()));
        } catch (JsonProcessingException e) {
            job.setPayload("{}");
        }
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return jobRepo.save(job);
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

    @Transactional
    public void processJob(NotificationJob job) {
        log.info("📧 Processing job ID={}, type={}, template={}", job.getId(), job.getType(), job.getTemplateKey());

        // Set to SENDING
        job.setStatus("SENDING");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepo.save(job);

        // load template
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

        job.setStatus(success ? "COMPLETED" : "FAILED");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepo.save(job);
        log.info("Job ID={} marked as {}", job.getId(), job.getStatus());
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

