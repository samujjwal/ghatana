package com.ghatana.phr.application.record;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Service interface for record summary and timeline workflow.
 *
 * @doc.type class
 * @doc.purpose Defines operations for patient record summary and timeline (PHR-F1-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface RecordService {

    /**
     * Get record summary.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return record summary
     */
    Promise<RecordSummary> getRecordSummary(PatientOperationContext ctx, String patientId);

    /**
     * Get record timeline.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return record timeline
     */
    Promise<RecordTimeline> getRecordTimeline(PatientOperationContext ctx, String patientId);

    /**
     * Get timeline by category.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @param category  record category
     * @return timeline entries for category
     */
    Promise<List<TimelineEntry>> getTimelineByCategory(PatientOperationContext ctx, String patientId, String category);

    // ── Response types ─────────────────────────────────────────────────────────

    record RecordSummary(
        String patientId,
        Map<String, Object> demographics,
        Map<String, Integer> recordCounts,
        String lastUpdated
    ) {}

    record RecordTimeline(
        String patientId,
        List<TimelineEntry> entries,
        String generatedAt
    ) {}

    record TimelineEntry(
        String entryId,
        String timestamp,
        String category,
        String type,
        String description,
        Map<String, Object> details
    ) {}
}
