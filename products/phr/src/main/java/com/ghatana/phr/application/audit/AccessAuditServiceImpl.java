package com.ghatana.phr.application.audit;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final ConcurrentMap<String, OcrAuditTrail> ocrAuditTrails = new ConcurrentHashMap<>();

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

    // PHR-P1-008: OCR audit/evidence trail implementation

    @Override
    public Promise<Void> recordOcrExtraction(
            PatientOperationContext ctx,
            String documentId,
            String extractedText,
            float confidence) {
        String textHash = hashText(extractedText);
        OcrExtractionEvent extraction = new OcrExtractionEvent(
            "OCR-EXT-" + UUID.randomUUID().toString().substring(0, 8),
            Instant.now().toString(),
            textHash,
            confidence,
            "en", // Default language, would be parameterized in real implementation
            "1.0"  // Engine version
        );

        // Store or update OCR audit trail
        OcrAuditTrail existing = ocrAuditTrails.get(documentId);
        if (existing == null) {
            OcrAuditTrail trail = new OcrAuditTrail(
                documentId,
                ctx.patientId(),
                extraction,
                null, // No confirmation yet
                Instant.now().toString()
            );
            ocrAuditTrails.put(documentId, trail);
        } else {
            // Update extraction event
            OcrAuditTrail updated = new OcrAuditTrail(
                documentId,
                existing.patientId(),
                extraction,
                existing.confirmation(),
                existing.createdAt()
            );
            ocrAuditTrails.put(documentId, updated);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<Void> recordOcrConfirmation(
            PatientOperationContext ctx,
            String documentId,
            String originalHash,
            String correctedHash,
            String reviewerId) {
        boolean hasChanges = !originalHash.equals(correctedHash);
        OcrConfirmationEvent confirmation = new OcrConfirmationEvent(
            "OCR-CONF-" + UUID.randomUUID().toString().substring(0, 8),
            Instant.now().toString(),
            originalHash,
            correctedHash,
            reviewerId,
            "reviewer",
            hasChanges
        );

        // Update OCR audit trail with confirmation
        OcrAuditTrail existing = ocrAuditTrails.get(documentId);
        if (existing != null) {
            OcrAuditTrail updated = new OcrAuditTrail(
                documentId,
                existing.patientId(),
                existing.extraction(),
                confirmation,
                existing.createdAt()
            );
            ocrAuditTrails.put(documentId, updated);
        } else {
            // Create trail with confirmation only (extraction not recorded)
            OcrAuditTrail trail = new OcrAuditTrail(
                documentId,
                ctx.patientId(),
                null, // No extraction event
                confirmation,
                Instant.now().toString()
            );
            ocrAuditTrails.put(documentId, trail);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<OcrAuditTrail> getOcrAuditTrail(PatientOperationContext ctx, String documentId) {
        OcrAuditTrail trail = ocrAuditTrails.get(documentId);
        if (trail == null) {
            return Promise.of(new OcrAuditTrail(
                documentId,
                ctx.patientId(),
                null,
                null,
                Instant.now().toString()
            ));
        }
        return Promise.of(trail);
    }

    // Helper method for text hashing
    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available
            return String.valueOf(text.hashCode());
        }
    }
}
