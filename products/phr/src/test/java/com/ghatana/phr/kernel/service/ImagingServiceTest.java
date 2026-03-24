package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.ImagingService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ImagingService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR imaging service — orders, studies, radiology reports
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ImagingService")
class ImagingServiceTest extends EventloopTestBase {

    private ImagingService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new ImagingService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        void serviceName() {
            assertEquals("imaging", service.getName());
        }
    }

    @Nested
    @DisplayName("createOrder")
    class OrderTests {

        @Test
        @DisplayName("stores order with REQUESTED status")
        void storesOrder() {
            ImagingOrder order = buildOrder("patient-1", "CT", null);

            ImagingOrder stored = runPromise(() -> service.createOrder(order));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(OrderStatus.REQUESTED);
            assertThat(stored.modalityCode()).isEqualTo("CT");
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createOrder(buildOrder(null, "CT", null))));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("registerStudy")
    class StudyTests {

        @Test
        @DisplayName("registers study with COMPLETE status")
        void registersStudy() {
            ImagingOrder order = runPromise(() -> service.createOrder(buildOrder("p1", "CT", null)));

            ImagingStudy study = buildStudy("p1", order.id(), "1.2.3.4.5");
            ImagingStudy stored = runPromise(() -> service.registerStudy(study));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(StudyStatus.COMPLETE);
            assertThat(stored.dcmStudyInstanceUid()).isEqualTo("1.2.3.4.5");
        }

        @Test
        @DisplayName("fulfills associated order when study is registered")
        void fulfillsAssociatedOrder() {
            ImagingOrder order = runPromise(() -> service.createOrder(buildOrder("p1", "MRI", null)));
            runPromise(() -> service.registerStudy(buildStudy("p1", order.id(), "1.2.840.1")));

            Optional<ImagingOrder> updated = runPromise(() -> service.getOrder(order.id()));

            assertTrue(updated.isPresent());
            assertThat(updated.get().status()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("storeReport")
    class ReportTests {

        @Test
        @DisplayName("stores radiology report")
        void storesReport() {
            ImagingOrder order = runPromise(() -> service.createOrder(buildOrder("p1", "CT", null)));
            ImagingStudy study = runPromise(() -> service.registerStudy(buildStudy("p1", order.id(), "1.2.3")));

            RadiologyReport report = buildReport("p1", study.id());
            RadiologyReport stored = runPromise(() -> service.storeReport(report));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(ReportStatus.FINAL);
        }
    }

    @Nested
    @DisplayName("getPatientStudies")
    class PatientStudyTests {

        @Test
        @DisplayName("returns all studies for patient")
        void returnsForPatient() {
            ImagingOrder o1 = runPromise(() -> service.createOrder(buildOrder("patient-A", "CT", null)));
            ImagingOrder o2 = runPromise(() -> service.createOrder(buildOrder("patient-A", "MRI", null)));
            runPromise(() -> service.registerStudy(buildStudy("patient-A", o1.id(), "1.2.3.111")));
            runPromise(() -> service.registerStudy(buildStudy("patient-A", o2.id(), "1.2.3.222")));
            runPromise(() -> service.registerStudy(buildStudy("patient-B", "order-B", "1.2.3.333")));

            List<ImagingStudy> studies = runPromise(() -> service.getPatientStudies("patient-A"));
            assertThat(studies).hasSize(2);
            assertThat(studies).allMatch(s -> "patient-A".equals(s.patientId()));
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static ImagingOrder buildOrder(String patientId, String modality, String id) {
        return new ImagingOrder(id, patientId, "enc-1", "radiologist-1",
                modality, "Chest CT", "Shortness of breath",
                OrderStatus.REQUESTED, Instant.now(), null);
    }

    private static ImagingStudy buildStudy(String patientId, String orderId, String dcmUid) {
        return new ImagingStudy(null, patientId, orderId, dcmUid,
                "CT", "pacs://server1/studies/" + dcmUid,
                5, 120, StudyStatus.COMPLETE,
                Instant.now(), null);
    }

    private static RadiologyReport buildReport(String patientId, String studyId) {
        return new RadiologyReport(null, patientId, studyId, "radiologist-1",
                "Normal chest CT. No acute findings.",
                "Unremarkable", null,
                ReportStatus.FINAL, Instant.now());
    }
}
