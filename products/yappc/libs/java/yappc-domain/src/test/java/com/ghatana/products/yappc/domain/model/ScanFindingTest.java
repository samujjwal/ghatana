package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScanFinding} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates ScanFinding entity behavior, status transitions, and severity checks
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ScanFinding Domain Model Tests")
class ScanFindingTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID SCAN_JOB_ID = UUID.randomUUID();
    private static final String FINDING_TYPE = "VULNERABILITY";
    private static final String SEVERITY = "HIGH";
    private static final String TITLE = "SQL Injection in UserController";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates finding with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE);

            // THEN
            assertThat(finding.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(finding.getScanJobId()).isEqualTo(SCAN_JOB_ID);
            assertThat(finding.getFindingType()).isEqualTo(FINDING_TYPE);
            assertThat(finding.getSeverity()).isEqualTo(SEVERITY);
            assertThat(finding.getTitle()).isEqualTo(TITLE);
            assertThat(finding.getStatus()).isEqualTo("OPEN");
            assertThat(finding.isFalsePositive()).isFalse();
            assertThat(finding.getCreatedAt()).isNotNull();
            assertThat(finding.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields")
        void ofThrowsForNullRequiredFields() {
            assertThatThrownBy(() -> ScanFinding.of(null, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, null, FINDING_TYPE, SEVERITY, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("scanJobId must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, null, SEVERITY, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("findingType must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, null, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("severity must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("title must not be null");
        }
    }

    @Nested
    @DisplayName("Status Transition Tests")
    class StatusTransitionTests {

        @Test
        @DisplayName("resolve() sets status to RESOLVED")
        void resolveSetsStatusResolved() {
            // GIVEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE);
            assertThat(finding.getStatus()).isEqualTo("OPEN");

            // WHEN
            ScanFinding result = finding.resolve();

            // THEN
            assertThat(result).isSameAs(finding);
            assertThat(finding.getStatus()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("markFalsePositive() sets falsePositive flag and status")
        void markFalsePositiveSetsFlags() {
            // GIVEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE);

            // WHEN
            ScanFinding result = finding.markFalsePositive();

            // THEN
            assertThat(result).isSameAs(finding);
            assertThat(finding.isFalsePositive()).isTrue();
            assertThat(finding.getStatus()).isEqualTo("FALSE_POSITIVE");
        }

        @Test
        @DisplayName("transitions update updatedAt")
        void transitionsUpdateTimestamp() throws InterruptedException {
            // GIVEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE);
            Instant original = finding.getUpdatedAt();
            Thread.sleep(10);

            // WHEN
            finding.resolve();

            // THEN
            assertThat(finding.getUpdatedAt()).isAfter(original);
        }
    }

    @Nested
    @DisplayName("Severity Check Tests")
    class SeverityCheckTests {

        @Test
        @DisplayName("isCriticalOrHigh() returns true for CRITICAL")
        void isCriticalOrHighReturnsTrueForCritical() {
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "CRITICAL", TITLE);
            assertThat(finding.isCriticalOrHigh()).isTrue();
        }

        @Test
        @DisplayName("isCriticalOrHigh() returns true for HIGH")
        void isCriticalOrHighReturnsTrueForHigh() {
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "HIGH", TITLE);
            assertThat(finding.isCriticalOrHigh()).isTrue();
        }

        @Test
        @DisplayName("isCriticalOrHigh() returns false for MEDIUM")
        void isCriticalOrHighReturnsFalseForMedium() {
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "MEDIUM", TITLE);
            assertThat(finding.isCriticalOrHigh()).isFalse();
        }

        @Test
        @DisplayName("isCriticalOrHigh() returns false for LOW")
        void isCriticalOrHighReturnsFalseForLow() {
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "LOW", TITLE);
            assertThat(finding.isCriticalOrHigh()).isFalse();
        }

        @Test
        @DisplayName("isCriticalOrHigh() is case-insensitive")
        void isCriticalOrHighIsCaseInsensitive() {
            ScanFinding finding1 = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "critical", TITLE);
            ScanFinding finding2 = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "High", TITLE);

            assertThat(finding1.isCriticalOrHigh()).isTrue();
            assertThat(finding2.isCriticalOrHigh()).isTrue();
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates finding with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            ScanFinding finding = ScanFinding.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .scanJobId(SCAN_JOB_ID)
                    .findingType(FINDING_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .description("Detailed description of the SQL injection vulnerability")
                    .remediation("Use parameterized queries instead of string concatenation")
                    .status("OPEN")
                    .falsePositive(false)
                    .cweId("CWE-89")
                    .cveId("CVE-2024-1234")
                    .cvssScore(9.8)
                    .createdAt(now)
                    .updatedAt(now)
                    .version(1)
                    .build();

            // THEN
            assertThat(finding.getId()).isEqualTo(id);
            assertThat(finding.getCweId()).isEqualTo("CWE-89");
            assertThat(finding.getCveId()).isEqualTo("CVE-2024-1234");
            assertThat(finding.getCvssScore()).isEqualTo(9.8);
            assertThat(finding.getDescription()).contains("SQL injection");
            assertThat(finding.getRemediation()).contains("parameterized queries");
        }

        @Test
        @DisplayName("builder defaults status to OPEN")
        void builderDefaultsStatusToOpen() {
            ScanFinding finding = ScanFinding.builder()
                    .workspaceId(WORKSPACE_ID)
                    .scanJobId(SCAN_JOB_ID)
                    .findingType(FINDING_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .build();

            assertThat(finding.getStatus()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("builder defaults falsePositive to false")
        void builderDefaultsFalsePositiveToFalse() {
            ScanFinding finding = ScanFinding.builder()
                    .workspaceId(WORKSPACE_ID)
                    .scanJobId(SCAN_JOB_ID)
                    .findingType(FINDING_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .build();

            assertThat(finding.isFalsePositive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            ScanFinding finding1 = ScanFinding.builder().id(id).severity("HIGH").build();
            ScanFinding finding2 = ScanFinding.builder().id(id).severity("LOW").build();

            assertThat(finding1).isEqualTo(finding2);
            assertThat(finding1.hashCode()).isEqualTo(finding2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            ScanFinding finding1 = ScanFinding.builder().id(UUID.randomUUID()).build();
            ScanFinding finding2 = ScanFinding.builder().id(UUID.randomUUID()).build();

            assertThat(finding1).isNotEqualTo(finding2);
        }
    }

    @Nested
    @DisplayName("Finding Type Tests")
    class FindingTypeTests {

        @Test
        @DisplayName("can set various finding types")
        void canSetVariousFindingTypes() {
            String[] types = {"VULNERABILITY", "CODE_SMELL", "BUG", "SECURITY_HOTSPOT", "DEPENDENCY"};

            for (String type : types) {
                ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, type, SEVERITY, TITLE);
                assertThat(finding.getFindingType()).isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("CVE/CWE Tests")
    class CveCweTests {

        @Test
        @DisplayName("can store CVE identifiers")
        void canStoreCveIdentifiers() {
            ScanFinding finding = ScanFinding.builder()
                    .workspaceId(WORKSPACE_ID)
                    .scanJobId(SCAN_JOB_ID)
                    .findingType(FINDING_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .cveId("CVE-2024-12345")
                    .build();

            assertThat(finding.getCveId()).isEqualTo("CVE-2024-12345");
        }

        @Test
        @DisplayName("can store CWE identifiers")
        void canStoreCweIdentifiers() {
            ScanFinding finding = ScanFinding.builder()
                    .workspaceId(WORKSPACE_ID)
                    .scanJobId(SCAN_JOB_ID)
                    .findingType(FINDING_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .cweId("CWE-79")
                    .build();

            assertThat(finding.getCweId()).isEqualTo("CWE-79");
        }

        @Test
        @DisplayName("can store CVSS scores")
        void canStoreCvssScores() {
            ScanFinding finding = ScanFinding.builder()
                    .workspaceId(WORKSPACE_ID)
                    .scanJobId(SCAN_JOB_ID)
                    .findingType(FINDING_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .cvssScore(7.5)
                    .build();

            assertThat(finding.getCvssScore()).isEqualTo(7.5);
        }
    }
}
