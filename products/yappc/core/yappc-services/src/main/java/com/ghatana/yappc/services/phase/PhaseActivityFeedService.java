package com.ghatana.yappc.services.phase;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Queries and maps phase activity feed entries.
 */
final class PhaseActivityFeedService {

    private static final Logger log = LoggerFactory.getLogger(PhaseActivityFeedService.class);

    private final AuditService auditService;

    PhaseActivityFeedService(@NotNull AuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    Promise<List<PhasePacket.ActivityFeedEntry>> queryActivityFeed(
            String phase,
            String projectId,
            String tenantId
    ) {
        try {
            Instant endDate = Instant.now();
            Instant startDate = endDate.minus(java.time.Duration.ofDays(30));

            return auditService.queryByPhase(projectId, phase, startDate, endDate)
                    .<List<PhasePacket.ActivityFeedEntry>>map(auditEvents -> {
                        if (auditEvents == null || auditEvents.isEmpty()) {
                            return List.of();
                        }

                        return auditEvents.stream()
                                .limit(50)
                                .map(event -> {
                                    Map<String, Object> details = event.getDetails();
                                    String outcome = activityOutcome(event.getSuccess(), details);
                                    Boolean success = activitySuccess(event.getSuccess(), outcome);
                                    return new PhasePacket.ActivityFeedEntry(
                                            event.getId(),
                                            event.getEventType(),
                                            activityAction(event.getEventType(), details),
                                            activitySummary(details),
                                            event.getPrincipal() != null ? event.getPrincipal() : "System",
                                            event.getTimestamp(),
                                            activitySeverity(success, details),
                                            event.getEventType(),
                                            success,
                                            outcome,
                                            activityCorrelationId(details)
                                    );
                                })
                                .toList();
                    })
                    .then((entries, error) -> {
                        if (error == null) {
                            return Promise.of(entries);
                        }
                        log.error("Error querying activity feed: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, error);
                        return Promise.of(List.of(new PhasePacket.ActivityFeedEntry(
                                "ACTIVITY_FEED_QUERY_FAILED",
                                "SYSTEM_DEGRADED",
                                "activity.feed.unavailable",
                                "Activity feed unavailable",
                                "System",
                                Instant.now(),
                                "ERROR",
                                "SYSTEM_DEGRADED",
                                false,
                                "FAILURE",
                                null)));
                    });

        } catch (Exception exception) {
            log.error("Error querying activity feed: phase={}, projectId={}, tenantId={}", phase, projectId, tenantId, exception);
            return Promise.of(List.of(new PhasePacket.ActivityFeedEntry(
                    "ACTIVITY_FEED_QUERY_FAILED",
                    "SYSTEM_DEGRADED",
                    "activity.feed.unavailable",
                    "Activity feed unavailable",
                    "System",
                    Instant.now(),
                    "ERROR",
                    "SYSTEM_DEGRADED",
                    false,
                    "FAILURE",
                    null)));
        }
    }

    private static String activityAction(String eventType, Map<String, Object> details) {
        return firstString(details, "action", "auditType", "operation", "command")
                .orElse(eventType != null ? eventType : "audit.event");
    }

    private static String activitySummary(Map<String, Object> details) {
        return firstString(details, "summary", "description", "message")
                .orElse("Audit event");
    }

    private static String activityOutcome(@Nullable Boolean success, Map<String, Object> details) {
        return firstString(details, "outcome", "status", "result")
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .orElseGet(() -> {
                    if (Boolean.FALSE.equals(success)) {
                        return "FAILURE";
                    }
                    if (Boolean.TRUE.equals(success)) {
                        return "SUCCESS";
                    }
                    return "UNKNOWN";
                });
    }

    @Nullable
    private static Boolean activitySuccess(@Nullable Boolean success, String outcome) {
        if (success != null) {
            return success;
        }
        if ("SUCCESS".equalsIgnoreCase(outcome) || "SUCCEEDED".equalsIgnoreCase(outcome)) {
            return true;
        }
        if ("FAILURE".equalsIgnoreCase(outcome) || "FAILED".equalsIgnoreCase(outcome) || "ERROR".equalsIgnoreCase(outcome)) {
            return false;
        }
        return null;
    }

    private static String activitySeverity(@Nullable Boolean success, Map<String, Object> details) {
        String severity = firstString(details, "severity", "level")
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .orElse(Boolean.FALSE.equals(success) ? "ERROR" : "INFO");
        if ("WARN".equals(severity)) {
            return "WARNING";
        }
        if ("WARNING".equals(severity) || "ERROR".equals(severity) || "INFO".equals(severity)) {
            return severity;
        }
        return Boolean.FALSE.equals(success) ? "ERROR" : "INFO";
    }

    @Nullable
    private static String activityCorrelationId(Map<String, Object> details) {
        return firstString(details, "correlationId", "correlation_id", "correlation", "requestId", "request_id")
                .orElse(null);
    }

    private static Optional<String> firstString(Map<String, Object> details, String... keys) {
        if (details == null || details.isEmpty()) {
            return Optional.empty();
        }
        for (String key : keys) {
            Object value = details.get(key);
            if (value != null && !value.toString().isBlank()) {
                return Optional.of(value.toString());
            }
        }
        return Optional.empty();
    }
}
