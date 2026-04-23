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

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final UUID SCAN_JOB_ID = UUID.randomUUID(); // GH-90000
    private static final String FINDING_TYPE = "VULNERABILITY";
    private static final String SEVERITY = "HIGH";
    private static final String TITLE = "SQL Injection in UserController";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates finding with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE); // GH-90000

            // THEN
            assertThat(finding.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(finding.getScanJobId()).isEqualTo(SCAN_JOB_ID); // GH-90000
            assertThat(finding.getFindingType()).isEqualTo(FINDING_TYPE); // GH-90000
            assertThat(finding.getSeverity()).isEqualTo(SEVERITY); // GH-90000
            assertThat(finding.getTitle()).isEqualTo(TITLE); // GH-90000
            assertThat(finding.getStatus()).isEqualTo("OPEN");
            assertThat(finding.isFalsePositive()).isFalse(); // GH-90000
            assertThat(finding.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(finding.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields")
        void ofThrowsForNullRequiredFields() { // GH-90000
            assertThatThrownBy(() -> ScanFinding.of(null, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, null, FINDING_TYPE, SEVERITY, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("scanJobId must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, null, SEVERITY, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("findingType must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, null, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("severity must not be null");

            assertThatThrownBy(() -> ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("title must not be null");
        }
    }

    @Nested
    @DisplayName("Status Transition Tests")
    class StatusTransitionTests {

        @Test
        @DisplayName("resolve() sets status to RESOLVED")
        void resolveSetsStatusResolved() { // GH-90000
            // GIVEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE); // GH-90000
            assertThat(finding.getStatus()).isEqualTo("OPEN");

            // WHEN
            ScanFinding result = finding.resolve(); // GH-90000

            // THEN
            assertThat(result).isSameAs(finding); // GH-90000
            assertThat(finding.getStatus()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("markFalsePositive() sets falsePositive flag and status")
        void markFalsePositiveSetsFlags() { // GH-90000
            // GIVEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE); // GH-90000

            // WHEN
            ScanFinding result = finding.markFalsePositive(); // GH-90000

            // THEN
            assertThat(result).isSameAs(finding); // GH-90000
            assertThat(finding.isFalsePositive()).isTrue(); // GH-90000
            assertThat(finding.getStatus()).isEqualTo("FALSE_POSITIVE");
        }

        @Test
        @DisplayName("transitions update updatedAt")
        void transitionsUpdateTimestamp() throws InterruptedException { // GH-90000
            // GIVEN
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, SEVERITY, TITLE); // GH-90000
            Instant original = finding.getUpdatedAt(); // GH-90000
            Thread.sleep(10); // GH-90000

            // WHEN
            finding.resolve(); // GH-90000

            // THEN
            assertThat(finding.getUpdatedAt()).isAfter(original); // GH-90000
        }
    }

    @Nested
    @DisplayName("Severity Check Tests")
    class SeverityCheckTests {

        @Test
        @DisplayName("isCriticalOrHigh() returns true for CRITICAL")
        void isCriticalOrHighReturnsTrueForCritical() { // GH-90000
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "CRITICAL", TITLE); // GH-90000
            assertThat(finding.isCriticalOrHigh()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isCriticalOrHigh() returns true for HIGH")
        void isCriticalOrHighReturnsTrueForHigh() { // GH-90000
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "HIGH", TITLE); // GH-90000
            assertThat(finding.isCriticalOrHigh()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isCriticalOrHigh() returns false for MEDIUM")
        void isCriticalOrHighReturnsFalseForMedium() { // GH-90000
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "MEDIUM", TITLE); // GH-90000
            assertThat(finding.isCriticalOrHigh()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isCriticalOrHigh() returns false for LOW")
        void isCriticalOrHighReturnsFalseForLow() { // GH-90000
            ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "LOW", TITLE); // GH-90000
            assertThat(finding.isCriticalOrHigh()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isCriticalOrHigh() is case-insensitive")
        void isCriticalOrHighIsCaseInsensitive() { // GH-90000
            ScanFinding finding1 = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "critical", TITLE); // GH-90000
            ScanFinding finding2 = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, FINDING_TYPE, "High", TITLE); // GH-90000

            assertThat(finding1.isCriticalOrHigh()).isTrue(); // GH-90000
            assertThat(finding2.isCriticalOrHigh()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates finding with all fields")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            ScanFinding finding = ScanFinding.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .scanJobId(SCAN_JOB_ID) // GH-90000
                    .findingType(FINDING_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .description("Detailed description of the SQL injection vulnerability")
                    .remediation("Use parameterized queries instead of string concatenation")
                    .status("OPEN")
                    .falsePositive(false) // GH-90000
                    .cweId("CWE-89")
                    .cveId("CVE-2024-1234")
                    .cvssScore(9.8) // GH-90000
                    .createdAt(now) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(1) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(finding.getId()).isEqualTo(id); // GH-90000
            assertThat(finding.getCweId()).isEqualTo("CWE-89");
            assertThat(finding.getCveId()).isEqualTo("CVE-2024-1234");
            assertThat(finding.getCvssScore()).isEqualTo(9.8); // GH-90000
            assertThat(finding.getDescription()).contains("SQL injection");
            assertThat(finding.getRemediation()).contains("parameterized queries");
        }

        @Test
        @DisplayName("builder defaults status to OPEN")
        void builderDefaultsStatusToOpen() { // GH-90000
            ScanFinding finding = ScanFinding.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .scanJobId(SCAN_JOB_ID) // GH-90000
                    .findingType(FINDING_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .build(); // GH-90000

            assertThat(finding.getStatus()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("builder defaults falsePositive to false")
        void builderDefaultsFalsePositiveToFalse() { // GH-90000
            ScanFinding finding = ScanFinding.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .scanJobId(SCAN_JOB_ID) // GH-90000
                    .findingType(FINDING_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .build(); // GH-90000

            assertThat(finding.isFalsePositive()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            ScanFinding finding1 = ScanFinding.builder().id(id).severity("HIGH").build();
            ScanFinding finding2 = ScanFinding.builder().id(id).severity("LOW").build();

            assertThat(finding1).isEqualTo(finding2); // GH-90000
            assertThat(finding1.hashCode()).isEqualTo(finding2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            ScanFinding finding1 = ScanFinding.builder().id(UUID.randomUUID()).build(); // GH-90000
            ScanFinding finding2 = ScanFinding.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(finding1).isNotEqualTo(finding2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Finding Type Tests")
    class FindingTypeTests {

        @Test
        @DisplayName("can set various finding types")
        void canSetVariousFindingTypes() { // GH-90000
            String[] types = {"VULNERABILITY", "CODE_SMELL", "BUG", "SECURITY_HOTSPOT", "DEPENDENCY"};

            for (String type : types) { // GH-90000
                ScanFinding finding = ScanFinding.of(WORKSPACE_ID, SCAN_JOB_ID, type, SEVERITY, TITLE); // GH-90000
                assertThat(finding.getFindingType()).isEqualTo(type); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("CVE/CWE Tests")
    class CveCweTests {

        @Test
        @DisplayName("can store CVE identifiers")
        void canStoreCveIdentifiers() { // GH-90000
            ScanFinding finding = ScanFinding.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .scanJobId(SCAN_JOB_ID) // GH-90000
                    .findingType(FINDING_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .cveId("CVE-2024-12345")
                    .build(); // GH-90000

            assertThat(finding.getCveId()).isEqualTo("CVE-2024-12345");
        }

        @Test
        @DisplayName("can store CWE identifiers")
        void canStoreCweIdentifiers() { // GH-90000
            ScanFinding finding = ScanFinding.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .scanJobId(SCAN_JOB_ID) // GH-90000
                    .findingType(FINDING_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .cweId("CWE-79")
                    .build(); // GH-90000

            assertThat(finding.getCweId()).isEqualTo("CWE-79");
        }

        @Test
        @DisplayName("can store CVSS scores")
        void canStoreCvssScores() { // GH-90000
            ScanFinding finding = ScanFinding.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .scanJobId(SCAN_JOB_ID) // GH-90000
                    .findingType(FINDING_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .cvssScore(7.5) // GH-90000
                    .build(); // GH-90000

            assertThat(finding.getCvssScore()).isEqualTo(7.5); // GH-90000
        }
    }
}
