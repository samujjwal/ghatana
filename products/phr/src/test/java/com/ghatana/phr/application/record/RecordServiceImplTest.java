package com.ghatana.phr.application.record;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for source-backed patient timeline aggregation.
 *
 * @doc.type class
 * @doc.purpose Verifies patient timeline entries come from clinical source services, not synthetic fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RecordServiceImpl")
@ExtendWith(MockitoExtension.class)
class RecordServiceImplTest extends EventloopTestBase {

    @Mock
    private MedicationService medicationService;

    @Mock
    private LabResultService labResultService;

    @Mock
    private ImmunizationService immunizationService;

    @Test
    @DisplayName("returns an empty timeline when no source services are wired")
    void emptyTimelineWhenNoSourcesAreWired() {
        RecordServiceImpl service = new RecordServiceImpl();

        RecordService.RecordTimeline timeline = runPromise(
            () -> service.getRecordTimeline(context(), "patient-1"));

        assertThat(timeline.entries()).isEmpty();
    }

    @Test
    @DisplayName("builds timeline entries from medication, lab, and immunization source services")
    void buildsTimelineFromSourceServices() {
        when(medicationService.getPrescriptionHistory("patient-1"))
            .thenReturn(Promise.of(List.of(prescription())));
        when(labResultService.getPatientObservations("patient-1"))
            .thenReturn(Promise.of(List.of(labObservation())));
        when(immunizationService.getImmunizationHistory("patient-1"))
            .thenReturn(Promise.of(List.of(immunization())));
        RecordServiceImpl service = new RecordServiceImpl(
            null,
            null,
            medicationService,
            labResultService,
            immunizationService,
            null,
            null
        );

        RecordService.RecordTimeline timeline = runPromise(
            () -> service.getRecordTimeline(context(), "patient-1"));

        assertThat(timeline.entries())
            .extracting(RecordService.TimelineEntry::category)
            .containsExactly("immunization", "lab", "medication");
        assertThat(timeline.entries())
            .allSatisfy(entry -> assertThat(entry.details()).containsKey("source"));
        assertThat(timeline.entries())
            .extracting(RecordService.TimelineEntry::description)
            .doesNotContain("Primary care visit", "New prescription", "Blood work");
    }

    @Test
    @DisplayName("filters category timeline without blocking the event loop")
    void filtersTimelineByCategory() {
        when(medicationService.getPrescriptionHistory("patient-1"))
            .thenReturn(Promise.of(List.of(prescription())));
        when(labResultService.getPatientObservations("patient-1"))
            .thenReturn(Promise.of(List.of(labObservation())));
        when(immunizationService.getImmunizationHistory("patient-1"))
            .thenReturn(Promise.of(List.of()));
        RecordServiceImpl service = new RecordServiceImpl(
            null,
            null,
            medicationService,
            labResultService,
            immunizationService,
            null,
            null
        );

        List<RecordService.TimelineEntry> labs = runPromise(
            () -> service.getTimelineByCategory(context(), "patient-1", "lab"));

        assertThat(labs).hasSize(1);
        assertThat(labs.get(0).entryId()).isEqualTo("lab:obs-1");
    }

    private static PatientOperationContext context() {
        return new PatientOperationContext("tenant-1", "patient-1", "patient-1");
    }

    private static MedicationService.Prescription prescription() {
        return new MedicationService.Prescription(
            "rx-1",
            "patient-1",
            "clinician-1",
            "enc-1",
            "C09A",
            "Lisinopril",
            "10 mg",
            "hypertension",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-03-01T00:00:00Z"),
            1,
            Duration.ofDays(30),
            MedicationService.PrescriptionStatus.ACTIVE
        );
    }

    private static LabResultService.LabObservation labObservation() {
        return new LabResultService.LabObservation(
            "obs-1",
            "patient-1",
            "enc-1",
            "order-1",
            "2160-0",
            "Creatinine",
            "Creatinine",
            new BigDecimal("0.8"),
            0.6,
            "mg/dL",
            "0.6-1.2",
            "lab-1",
            Instant.parse("2026-01-02T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z"),
            LabResultService.ObservationStatus.FINAL,
            null,
            "N"
        );
    }

    private static ImmunizationService.ImmunizationRecord immunization() {
        return new ImmunizationService.ImmunizationRecord(
            "imm-1",
            "patient-1",
            "enc-1",
            "21",
            "Varicella",
            "clinician-1",
            Instant.parse("2026-01-04T00:00:00Z"),
            Instant.parse("2026-01-04T01:00:00Z"),
            "LOT-1",
            LocalDate.parse("2027-01-01"),
            "IM",
            "Varicella",
            1,
            false,
            null,
            ImmunizationService.ImmunizationStatus.ADMINISTERED
        );
    }
}
