package com.scheduler.demo.controller;

import com.scheduler.demo.dto.CreateNotificationRequest;
import com.scheduler.demo.dto.NotificationResponse;
import com.scheduler.demo.model.NotificationJob;
import com.scheduler.demo.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<List<NotificationResponse>> create(@Valid @RequestBody List<CreateNotificationRequest> req) {
        if (req == null || req.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<NotificationResponse> jobs = new ArrayList<>();
        for (CreateNotificationRequest r : req) {
            NotificationJob job = notificationService.createJob(r);
            jobs.add(new NotificationResponse(job.getId(), job.getStatus(), job.getSendAt(), job.getRecipientEmail(), job.getUserName()));
        }
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> get(@PathVariable Long id) {
        return notificationService.findById(id)
                .map(j -> ResponseEntity.ok(new NotificationResponse(j.getId(), j.getStatus(), j.getSendAt(), j.getRecipientEmail(), j.getUserName())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        boolean ok = notificationService.cancelJob(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(@RequestParam(value="status", required=false) String status) {
        // naive: load all and filter
        List<NotificationResponse> list = notificationService
                .findAllByStatus(status)
                .stream()
                .map(j -> new NotificationResponse(j.getId(), j.getStatus(), j.getSendAt(), j.getRecipientEmail(), j.getUserName()))
                .toList();
        return ResponseEntity.ok(list);
    }
}

