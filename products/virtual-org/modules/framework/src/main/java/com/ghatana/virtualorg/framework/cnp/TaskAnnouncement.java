package com.ghatana.virtualorg.framework.cnp;

import com.ghatana.virtualorg.framework.task.TaskDefinition;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a task announcement in the Contract Net Protocol.
 *
 * <p><b>Purpose</b><br>
 * A TaskAnnouncement is broadcast to potential contractors (agents) who
 * can bid on the task. This is the first step in the CNP workflow.
 *
 * <p><b>CNP Workflow</b><br>
 * 1. Manager announces task -> TaskAnnouncement
 * 2. Agents submit bids -> TaskBid
 * 3. Manager selects winner -> Contract awarded
 * 4. Winner executes task -> Result reported
 *
 * @doc.type record
 * @doc.purpose Task announcement for CNP bidding
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record TaskAnnouncement(
        String id,
        TaskDefinition task,
        String managerId,
        String departmentId,
        Instant announcedAt,
        Instant biddingDeadline,
        Map<String, Object> requirements,
        AnnouncementStatus status
) {

    /**
     * Compact constructor with defaults.
     */
    public TaskAnnouncement {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        announcedAt = announcedAt != null ? announcedAt : Instant.now();
        requirements = requirements != null ? Map.copyOf(requirements) : Map.of();
        status = status != null ? status : AnnouncementStatus.OPEN;
    }

    /**
     * Creates a new task announcement.
     */
    public static TaskAnnouncement create(
            TaskDefinition task,
            String managerId,
            String departmentId,
            Instant biddingDeadline) {
        return new TaskAnnouncement(
                null,
                task,
                managerId,
                departmentId,
                Instant.now(),
                biddingDeadline,
                Map.of(),
                AnnouncementStatus.OPEN
        );
    }

    /**
     * Creates a copy with updated status.
     */
    public TaskAnnouncement withStatus(AnnouncementStatus newStatus) {
        return new TaskAnnouncement(
                id, task, managerId, departmentId, announcedAt, biddingDeadline, requirements, newStatus
        );
    }

    /**
     * Checks if bidding is still open.
     */
    public boolean isBiddingOpen() {
        return status == AnnouncementStatus.OPEN && Instant.now().isBefore(biddingDeadline);
    }

    /**
     * Announcement status.
     */
    public enum AnnouncementStatus {
        OPEN,       // Accepting bids
        CLOSED,     // Bidding period ended
        AWARDED,    // Contract awarded
        CANCELLED   // Task cancelled
    }
}
