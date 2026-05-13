package com.ghatana.yappc.services.phase;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQuery;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Degraded audit adapter used when a platform audit backend is unavailable.
 *
 * @doc.type class
 * @doc.purpose Explicit degraded AuditService adapter for phase packet wiring
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DegradedAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(DegradedAuditService.class);

    private final String degradedReason;

    public DegradedAuditService(@NotNull String degradedReason) {
        this.degradedReason = degradedReason;
        log.warn("Using DegradedAuditService: {}", degradedReason);
    }

    @Override
    public Promise<Void> record(AuditEvent event) {
        log.warn("Audit record dropped in degraded mode: eventType={}, reason={}",
            event != null ? event.getEventType() : "unknown", degradedReason);
        return Promise.complete();
    }

    @Override
    public Promise<List<AuditEvent>> query(AuditQuery query) {
        log.warn("Audit query served in degraded mode: reason={}", degradedReason);
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
        log.warn("Audit project query served in degraded mode: projectId={}, reason={}", projectId, degradedReason);
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
        log.warn("Audit phase query served in degraded mode: projectId={}, phase={}, reason={}",
            projectId, phase, degradedReason);
        return Promise.of(List.of());
    }
}
