package com.scheduler.demo.repository;


import com.scheduler.demo.model.NotificationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {
    List<NotificationJob> findByStatusAndSendAtBefore(String status, LocalDateTime time);

    @Query("select n from NotificationJob n where n.status = :status")
    List<NotificationJob> fetchPending(@Param("status") String status);

    /**
     * Fetch pending jobs with pessimistic locking to prevent race conditions.
     * Uses SKIP LOCKED to avoid blocking - each instance gets different jobs.
     * This is better for multi-instance deployments.
     */
    @Query(value = "SELECT * FROM notification_jobs " +
                   "WHERE status = :status AND send_at <= :now " +
                   "ORDER BY send_at " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<NotificationJob> fetchPendingWithLock(
        @Param("status") String status,
        @Param("now") LocalDateTime now,
        @Param("limit") int limit
    );
}

