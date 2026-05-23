package com.ghatana.phr.application.record;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of RecordService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides record summary and timeline operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class RecordServiceImpl implements RecordService {

    @Override
    public Promise<RecordSummary> getRecordSummary(PatientOperationContext ctx, String patientId) {
        RecordSummary summary = new RecordSummary(
            patientId,
            Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "dateOfBirth", "1980-01-01"
            ),
            Map.of(
                "encounters", 5,
                "medications", 3,
                "allergies", 1,
                "conditions", 2,
                "labs", 10,
                "immunizations", 4,
                "documents", 2
            ),
            Instant.now().toString()
        );
        return Promise.of(summary);
    }

    @Override
    public Promise<RecordTimeline> getRecordTimeline(PatientOperationContext ctx, String patientId) {
        List<TimelineEntry> entries = List.of(
            new TimelineEntry(
                "ENTRY-" + UUID.randomUUID().toString().substring(0, 8),
                "2024-01-15T10:00:00Z",
                "encounter",
                "visit",
                "Primary care visit",
                Map.of("provider", "Dr. Smith", "location", "Main Clinic")
            ),
            new TimelineEntry(
                "ENTRY-" + UUID.randomUUID().toString().substring(0, 8),
                "2024-01-10T14:30:00Z",
                "medications",
                "prescription",
                "New prescription",
                Map.of("medication", "Lisinopril", "dosage", "10mg")
            ),
            new TimelineEntry(
                "ENTRY-" + UUID.randomUUID().toString().substring(0, 8),
                "2024-01-05T09:00:00Z",
                "labs",
                "test",
                "Blood work",
                Map.of("testType", "CBC", "result", "Normal")
            )
        );

        RecordTimeline timeline = new RecordTimeline(
            patientId,
            entries,
            Instant.now().toString()
        );

        return Promise.of(timeline);
    }

    @Override
    public Promise<List<TimelineEntry>> getTimelineByCategory(PatientOperationContext ctx, String patientId, String category) {
        RecordTimeline timeline = getRecordTimeline(ctx, patientId).getResult();
        List<TimelineEntry> filtered = timeline.entries().stream()
            .filter(e -> e.category().equals(category))
            .toList();
        return Promise.of(filtered);
    }
}
