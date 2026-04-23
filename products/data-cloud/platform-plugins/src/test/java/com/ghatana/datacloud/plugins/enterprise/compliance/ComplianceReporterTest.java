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
    void setUp() { // GH-90000
        reporter = new ComplianceReporter(); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create reporter without errors")
        void shouldCreateReporter() { // GH-90000
            assertThatCode(() -> new ComplianceReporter()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should initialize SOC2 controls on construction")
        void shouldInitializeSOC2Controls() { // GH-90000
            SOC2Dashboard dashboard = runPromise(() -> reporter.getSOC2Dashboard()); // GH-90000
            assertThat(dashboard).isNotNull(); // GH-90000
            assertThat(dashboard.getTotalControls()).isGreaterThan(0); // GH-90000
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
        void shouldCreateDSARWithPendingStatus() { // GH-90000
            DSARRequest request = runPromise(() -> // GH-90000
                    reporter.createDSAR("alice@example.com", "user-123", "admin")); // GH-90000

            assertThat(request).isNotNull(); // GH-90000
            assertThat(request.getRequestId()).isNotBlank(); // GH-90000
            assertThat(request.getSubjectEmail()).isEqualTo("alice@example.com");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING); // GH-90000
        }

        @Test
        @DisplayName("should generate unique request IDs for different DSAR requests")
        void shouldGenerateUniqueRequestIds() { // GH-90000
            DSARRequest first = runPromise(() -> // GH-90000
                    reporter.createDSAR("alice@example.com", "u-1", "admin")); // GH-90000
            DSARRequest second = runPromise(() -> // GH-90000
                    reporter.createDSAR("bob@example.com", "u-2", "admin")); // GH-90000

            assertThat(first.getRequestId()).isNotEqualTo(second.getRequestId()); // GH-90000
        }
    }

    @Nested
    @DisplayName("processDSAR")
    class ProcessDSAR {

        @Test
        @DisplayName("should process DSAR and return collected data")
        void shouldProcessDSAR() { // GH-90000
            DSARRequest request = runPromise(() -> // GH-90000
                    reporter.createDSAR("alice@example.com", "u-1", "admin")); // GH-90000

            DataCategory category = DataCategory.builder() // GH-90000
                    .categoryName("profile")
                    .recordCount(3) // GH-90000
                    .dataFields(List.of("email", "name")) // GH-90000
                    .build(); // GH-90000

            DSARResponse response = runPromise(() -> // GH-90000
                    reporter.processDSAR(request.getRequestId(), // GH-90000
                            (email, subjectId) -> List.of(category))); // GH-90000

            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.getTotalRecords()).isEqualTo(3); // GH-90000
            assertThat(response.getDataCategories()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should throw when DSAR request not found")
        void shouldThrowForUnknownDSAR() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> reporter.processDSAR("nonexistent-id", (e, s) -> List.of()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
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
        void shouldCreateErasureRequest() { // GH-90000
            ErasureRequest request = runPromise(() -> // GH-90000
                    reporter.createErasureRequest("alice@example.com", "u-1", "admin", // GH-90000
                            ErasureScope.FULL));

            assertThat(request).isNotNull(); // GH-90000
            assertThat(request.getRequestId()).isNotBlank(); // GH-90000
            assertThat(request.getScope()).isEqualTo(ErasureScope.FULL); // GH-90000
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING); // GH-90000
        }
    }

    @Nested
    @DisplayName("executeErasure")
    class ExecuteErasure {

        @Test
        @DisplayName("should execute erasure and return result")
        void shouldExecuteErasure() { // GH-90000
            ErasureRequest request = runPromise(() -> // GH-90000
                    reporter.createErasureRequest("alice@example.com", "u-1", "admin", // GH-90000
                            ErasureScope.PARTIAL));

            ErasureResult result = runPromise(() -> // GH-90000
                    reporter.executeErasure(request.getRequestId(), // GH-90000
                            (email, subjectId, scope) -> ErasureResult.builder() // GH-90000
                                    .requestId(request.getRequestId()) // GH-90000
                                    .success(true) // GH-90000
                                    .deletedRecords(5) // GH-90000
                                    .anonymizedRecords(2) // GH-90000
                                    .completedAt(Instant.now()) // GH-90000
                                    .build())); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getDeletedRecords()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("should throw when erasure request not found")
        void shouldThrowForUnknownErasureRequest() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> reporter.executeErasure("missing-id", // GH-90000
                            (email, subjectId, scope) -> ErasureResult.builder() // GH-90000
                                    .requestId("missing-id")
                                    .success(false) // GH-90000
                                    .build()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
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
        void shouldLogHIPAAAuditEntry() { // GH-90000
            reporter.logHIPAAAudit("READ", "doctor-1", "PATIENT_RECORD", "patient-42", // GH-90000
                    Map.of("purpose", "treatment")); // GH-90000

            Instant start = Instant.now().minus(Duration.ofSeconds(1)); // GH-90000
            Instant end = Instant.now().plus(Duration.ofSeconds(1)); // GH-90000
            List<AuditLogEntry> entries = runPromise(() -> // GH-90000
                    reporter.exportHIPAAAuditLog(start, end)); // GH-90000

            assertThat(entries).isNotEmpty(); // GH-90000
            assertThat(entries.get(0).getActor()).isEqualTo("doctor-1");
        }

        @Test
        @DisplayName("should return empty list when no entries match date range")
        void shouldReturnEmptyForOutOfRangeDates() { // GH-90000
            reporter.logHIPAAAudit("READ", "doctor-1", "PATIENT_RECORD", "patient-1", null); // GH-90000

            Instant futureStart = Instant.now().plus(Duration.ofDays(1)); // GH-90000
            Instant futureEnd = futureStart.plus(Duration.ofDays(1)); // GH-90000
            List<AuditLogEntry> entries = runPromise(() -> // GH-90000
                    reporter.exportHIPAAAuditLog(futureStart, futureEnd)); // GH-90000

            assertThat(entries).isEmpty(); // GH-90000
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
        void shouldReturnSOC2DashboardWithControls() { // GH-90000
            SOC2Dashboard dashboard = runPromise(() -> reporter.getSOC2Dashboard()); // GH-90000

            assertThat(dashboard).isNotNull(); // GH-90000
            assertThat(dashboard.getTotalControls()).isGreaterThan(0); // GH-90000
            assertThat(dashboard.getControls()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should update SOC2 control status")
        void shouldUpdateSOC2ControlStatus() { // GH-90000
            SOC2Dashboard dashboard = runPromise(() -> reporter.getSOC2Dashboard()); // GH-90000
            String firstControlId = dashboard.getControls().get(0).getControlId(); // GH-90000

            SOC2Control updated = runPromise(() -> // GH-90000
                    reporter.updateSOC2Control(firstControlId, ControlStatus.COMPLIANT, "auditor-1", // GH-90000
                            "Passed all checks"));

            assertThat(updated).isNotNull(); // GH-90000
            assertThat(updated.getStatus()).isEqualTo(ControlStatus.COMPLIANT); // GH-90000
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
        void shouldSetRetentionPolicy() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .policyName("orders-retention")
                    .retentionDays(365) // GH-90000
                    .legalBasis("LEGAL_OBLIGATION")
                    .autoEnforce(false) // GH-90000
                    .build(); // GH-90000

            RetentionPolicy saved = runPromise(() -> // GH-90000
                    reporter.setRetentionPolicy("dataset-orders", policy)); // GH-90000

            assertThat(saved).isNotNull(); // GH-90000
            assertThat(saved.getRetentionDays()).isEqualTo(365); // GH-90000
            assertThat(saved.getPolicyName()).isEqualTo("orders-retention");
        }

        @Test
        @DisplayName("should enforce retention policies using provided data deleter")
        void shouldEnforceRetentionPolicies() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .policyName("logs-retention")
                    .retentionDays(30) // GH-90000
                    .legalBasis("OPERATIONAL")
                    .autoEnforce(true) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> reporter.setRetentionPolicy("dataset-logs", policy)); // GH-90000

            List<RetentionEnforcementResult> results = runPromise(() -> // GH-90000
                    reporter.enforceRetentionPolicies((datasetId, cutoffDate) -> 0)); // GH-90000
            assertThat(results).isNotNull(); // GH-90000
        }
    }
}
