package com.scheduler.demo.scheduler;

import com.scheduler.demo.model.NotificationJob;
import com.scheduler.demo.repository.NotificationJobRepository;
import com.scheduler.demo.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class NotificationScheduler {

    private final NotificationJobRepository jobRepository;
    private final NotificationService notificationService;
    private final Executor notificationExecutor;

    public NotificationScheduler(NotificationJobRepository jobRepository,
                                 NotificationService notificationService,
                                 Executor notificationExecutor) {
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.notificationExecutor = notificationExecutor;
    }

    // Runs every minute (configurable)
    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-seconds}000")
    public void pollPendingJobs() {
        log.info("Polling for pending jobs...");
        List<NotificationJob> pending = jobRepository.fetchPending("PENDING");
        log.info("Found {} pending jobs.", pending.size());
        if (pending.isEmpty()) return;
        for (NotificationJob job : pending) {
            // submit async
            CompletableFuture.runAsync(() -> {
                try {
                    notificationService.processJob(job);
                } catch (Exception ex) {
                    // log and mark failed
                    job.setStatus("FAILED");
                    job.setUpdatedAt(LocalDateTime.now());
                    job.setRecipientEmail(job.getRecipientEmail());
                    job.setUserName(job.getUserName());
                    jobRepository.save(job);
                    ex.printStackTrace();
                }
            }, notificationExecutor);
        }
    }
}

