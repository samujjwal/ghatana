package com.ghatana.phr.application.audit;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of AccessAuditService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides access audit operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class AccessAuditServiceImpl implements AccessAuditService {

    private final ConcurrentMap<String, List<AccessEvent>> accessLogs = new ConcurrentHashMap<>();

    @Override
    public Promise<AccessLog> getAccessLog(PatientOperationContext ctx, String patientId) {
        List<AccessEvent> events = accessLogs.getOrDefault(patientId, List.of());
        AccessLog log = new AccessLog(patientId, events, Instant.now().toString());
        return Promise.of(log);
    }

    @Override
    public Promise<AccessSummary> getAccessSummary(PatientOperationContext ctx, String patientId) {
        List<AccessEvent> events = accessLogs.getOrDefault(patientId, List.of());
        
        int totalAccesses = events.size();
        int authorizedAccesses = (int) events.stream().filter(AccessEvent::authorized).count();
        int unauthorizedAccesses = totalAccesses - authorizedAccesses;
        
        Map<String, Integer> accessByUser = Map.of(
            "user1", 5,
            "user2", 3
        );
        
        Map<String, Integer> accessByAction = Map.of(
            "view", 6,
            "update", 2
        );
        
        String lastAccessAt = events.isEmpty() ? null : events.get(events.size() - 1).timestamp();
        
        AccessSummary summary = new AccessSummary(
            patientId,
            totalAccesses,
            authorizedAccesses,
            unauthorizedAccesses,
            accessByUser,
            accessByAction,
            lastAccessAt
        );
        
        return Promise.of(summary);
    }

    @Override
    public Promise<List<AccessAnomaly>> getAccessAnomalies(PatientOperationContext ctx, String patientId) {
        List<AccessAnomaly> anomalies = List.of(
            new AccessAnomaly(
                "ANOM-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.now().toString(),
                "UNUSUAL_TIME",
                "Access at unusual time",
                "user1",
                "MEDIUM"
            )
        );
        return Promise.of(anomalies);
    }

    @Override
    public Promise<AuditReport> generateAuditReport(PatientOperationContext ctx, String patientId) {
        List<AccessEvent> events = accessLogs.getOrDefault(patientId, List.of());
        List<AccessAnomaly> anomalies = getAccessAnomalies(ctx, patientId).getResult();
        
        AuditReport report = new AuditReport(
            "REPORT-" + UUID.randomUUID().toString().substring(0, 8),
            patientId,
            Instant.now().toString(),
            ctx.userId(),
            Map.of("totalEvents", events.size(), "totalAnomalies", anomalies.size()),
            events,
            anomalies
        );
        
        return Promise.of(report);
    }
}
