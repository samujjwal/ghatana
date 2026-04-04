package com.ghatana.datacloud.plugins.enterprise.compliance;

import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.AuditLogEntry;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.ControlStatus;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.DSARRequest;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.DSARResponse;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.DataCategory;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.ErasureRequest;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.ErasureResult;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.ErasureScope;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.RequestStatus;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.RetentionPolicy;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.RetentionEnforcementResult;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.SOC2Control;
import com.ghatana.datacloud.plugins.enterprise.compliance.ComplianceReporter.SOC2Dashboard;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ComplianceReporter}.
 *
 * <p>ComplianceReporter is entirely in-memory and has no external dependencies,
 * making it straightforward to test synchronously via {@code EventloopTestBase}.
 *
 * @doc.type test
 * @doc.purpose Validate GDPR DSAR, erasure, HIPAA audit logging, SOC2 controls, and retention policies
 * @doc.layer product
 */
@DisplayName("ComplianceReporter Tests")
class ComplianceReporterTest extends EventloopTestBase {

    private ComplianceReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new ComplianceReporter();
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create reporter without errors")
        void shouldCreateReporter() {
            assertThatCode(() -> new ComplianceReporter()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should initialize SOC2 controls on construction")
        void shouldInitializeSOC2Controls() {
            SOC2Dashboard dashboard = runPromise(() -> reporter.getSOC2Dashboard());
            assertThat(dashboard).isNotNull();
            assertThat(dashboard.getTotalControls()).isGreaterThan(0);
        }
    }

    // =========================================================================
    // GDPR DSAR
    // =========================================================================

    @Nested
    @DisplayName("createDSAR")
    class CreateDSAR {

        @Test
        @DisplayName("should create DSAR with PENDING status")
        void shouldCreateDSARWithPendingStatus() {
            DSARRequest request = runPromise(() ->
                    reporter.createDSAR("alice@example.com", "user-123", "admin"));

            assertThat(request).isNotNull();
            assertThat(request.getRequestId()).isNotBlank();
            assertThat(request.getSubjectEmail()).isEqualTo("alice@example.com");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        }

        @Test
        @DisplayName("should generate unique request IDs for different DSAR requests")
        void shouldGenerateUniqueRequestIds() {
            DSARRequest first = runPromise(() ->
                    reporter.createDSAR("alice@example.com", "u-1", "admin"));
            DSARRequest second = runPromise(() ->
                    reporter.createDSAR("bob@example.com", "u-2", "admin"));

            assertThat(first.getRequestId()).isNotEqualTo(second.getRequestId());
        }
    }

    @Nested
    @DisplayName("processDSAR")
    class ProcessDSAR {

        @Test
        @DisplayName("should process DSAR and return collected data")
        void shouldProcessDSAR() {
            DSARRequest request = runPromise(() ->
                    reporter.createDSAR("alice@example.com", "u-1", "admin"));

            DataCategory category = DataCategory.builder()
                    .categoryName("profile")
                    .recordCount(3)
                    .dataFields(List.of("email", "name"))
                    .build();

            DSARResponse response = runPromise(() ->
                    reporter.processDSAR(request.getRequestId(),
                            (email, subjectId) -> List.of(category)));

            assertThat(response).isNotNull();
            assertThat(response.getTotalRecords()).isEqualTo(3);
            assertThat(response.getDataCategories()).hasSize(1);
        }

        @Test
        @DisplayName("should throw when DSAR request not found")
        void shouldThrowForUnknownDSAR() {
            assertThatThrownBy(() ->
                    runPromise(() -> reporter.processDSAR("nonexistent-id", (e, s) -> List.of())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // GDPR ERASURE
    // =========================================================================

    @Nested
    @DisplayName("createErasureRequest")
    class CreateErasureRequest {

        @Test
        @DisplayName("should create erasure request with PENDING status")
        void shouldCreateErasureRequest() {
            ErasureRequest request = runPromise(() ->
                    reporter.createErasureRequest("alice@example.com", "u-1", "admin",
                            ErasureScope.FULL));

            assertThat(request).isNotNull();
            assertThat(request.getRequestId()).isNotBlank();
            assertThat(request.getScope()).isEqualTo(ErasureScope.FULL);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("executeErasure")
    class ExecuteErasure {

        @Test
        @DisplayName("should execute erasure and return result")
        void shouldExecuteErasure() {
            ErasureRequest request = runPromise(() ->
                    reporter.createErasureRequest("alice@example.com", "u-1", "admin",
                            ErasureScope.PARTIAL));

            ErasureResult result = runPromise(() ->
                    reporter.executeErasure(request.getRequestId(),
                            (email, subjectId, scope) -> ErasureResult.builder()
                                    .requestId(request.getRequestId())
                                    .success(true)
                                    .deletedRecords(5)
                                    .anonymizedRecords(2)
                                    .completedAt(Instant.now())
                                    .build()));

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDeletedRecords()).isEqualTo(5);
        }

        @Test
        @DisplayName("should throw when erasure request not found")
        void shouldThrowForUnknownErasureRequest() {
            assertThatThrownBy(() ->
                    runPromise(() -> reporter.executeErasure("missing-id",
                            (email, subjectId, scope) -> ErasureResult.builder()
                                    .requestId("missing-id")
                                    .success(false)
                                    .build())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // HIPAA AUDIT LOG
    // =========================================================================

    @Nested
    @DisplayName("logHIPAAAudit and exportHIPAAAuditLog")
    class HIPAAAuditLog {

        @Test
        @DisplayName("should log HIPAA audit entry")
        void shouldLogHIPAAAuditEntry() {
            reporter.logHIPAAAudit("READ", "doctor-1", "PATIENT_RECORD", "patient-42",
                    Map.of("purpose", "treatment"));

            Instant start = Instant.now().minus(Duration.ofSeconds(1));
            Instant end = Instant.now().plus(Duration.ofSeconds(1));
            List<AuditLogEntry> entries = runPromise(() ->
                    reporter.exportHIPAAAuditLog(start, end));

            assertThat(entries).isNotEmpty();
            assertThat(entries.get(0).getActor()).isEqualTo("doctor-1");
        }

        @Test
        @DisplayName("should return empty list when no entries match date range")
        void shouldReturnEmptyForOutOfRangeDates() {
            reporter.logHIPAAAudit("READ", "doctor-1", "PATIENT_RECORD", "patient-1", null);

            Instant futureStart = Instant.now().plus(Duration.ofDays(1));
            Instant futureEnd = futureStart.plus(Duration.ofDays(1));
            List<AuditLogEntry> entries = runPromise(() ->
                    reporter.exportHIPAAAuditLog(futureStart, futureEnd));

            assertThat(entries).isEmpty();
        }
    }

    // =========================================================================
    // SOC2 COMPLIANCE
    // =========================================================================

    @Nested
    @DisplayName("SOC2 compliance")
    class SOC2Compliance {

        @Test
        @DisplayName("should return SOC2 dashboard with populated controls")
        void shouldReturnSOC2DashboardWithControls() {
            SOC2Dashboard dashboard = runPromise(() -> reporter.getSOC2Dashboard());

            assertThat(dashboard).isNotNull();
            assertThat(dashboard.getTotalControls()).isGreaterThan(0);
            assertThat(dashboard.getControls()).isNotEmpty();
        }

        @Test
        @DisplayName("should update SOC2 control status")
        void shouldUpdateSOC2ControlStatus() {
            SOC2Dashboard dashboard = runPromise(() -> reporter.getSOC2Dashboard());
            String firstControlId = dashboard.getControls().get(0).getControlId();

            SOC2Control updated = runPromise(() ->
                    reporter.updateSOC2Control(firstControlId, ControlStatus.COMPLIANT, "auditor-1",
                            "Passed all checks"));

            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(ControlStatus.COMPLIANT);
        }
    }

    // =========================================================================
    // RETENTION POLICIES
    // =========================================================================

    @Nested
    @DisplayName("setRetentionPolicy")
    class RetentionPolicies {

        @Test
        @DisplayName("should set retention policy for a dataset")
        void shouldSetRetentionPolicy() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .policyName("orders-retention")
                    .retentionDays(365)
                    .legalBasis("LEGAL_OBLIGATION")
                    .autoEnforce(false)
                    .build();

            RetentionPolicy saved = runPromise(() ->
                    reporter.setRetentionPolicy("dataset-orders", policy));

            assertThat(saved).isNotNull();
            assertThat(saved.getRetentionDays()).isEqualTo(365);
            assertThat(saved.getPolicyName()).isEqualTo("orders-retention");
        }

        @Test
        @DisplayName("should enforce retention policies using provided data deleter")
        void shouldEnforceRetentionPolicies() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .policyName("logs-retention")
                    .retentionDays(30)
                    .legalBasis("OPERATIONAL")
                    .autoEnforce(true)
                    .build();
            runPromise(() -> reporter.setRetentionPolicy("dataset-logs", policy));

            List<RetentionEnforcementResult> results = runPromise(() ->
                    reporter.enforceRetentionPolicies((datasetId, cutoffDate) -> 0));
            assertThat(results).isNotNull();
        }
    }
}
