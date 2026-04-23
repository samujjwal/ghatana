package com.ghatana.products.yappc.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScanType} enum.
 *
 * @doc.type class
 * @doc.purpose Validates ScanType enum values, descriptions, and comprehensive scan detection
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ScanType Enum Tests")
class ScanTypeTest {

    @Nested
    @DisplayName("Display Name Tests")
    class DisplayNameTests {

        @Test
        @DisplayName("SAST has correct display name")
        void sastHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.SAST.getDisplayName()).isEqualTo("Static Analysis");
        }

        @Test
        @DisplayName("DAST has correct display name")
        void dastHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.DAST.getDisplayName()).isEqualTo("Dynamic Analysis");
        }

        @Test
        @DisplayName("SCA has correct display name")
        void scaHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.SCA.getDisplayName()).isEqualTo("Dependency Scan");
        }

        @Test
        @DisplayName("IAC has correct display name")
        void iacHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.IAC.getDisplayName()).isEqualTo("IaC Scan");
        }

        @Test
        @DisplayName("CONTAINER has correct display name")
        void containerHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.CONTAINER.getDisplayName()).isEqualTo("Container Scan");
        }

        @Test
        @DisplayName("SECRET has correct display name")
        void secretHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.SECRET.getDisplayName()).isEqualTo("Secret Detection");
        }

        @Test
        @DisplayName("FULL has correct display name")
        void fullHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.FULL.getDisplayName()).isEqualTo("Full Scan");
        }

        @Test
        @DisplayName("QUICK has correct display name")
        void quickHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.QUICK.getDisplayName()).isEqualTo("Quick Scan");
        }

        @Test
        @DisplayName("INCREMENTAL has correct display name")
        void incrementalHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.INCREMENTAL.getDisplayName()).isEqualTo("Incremental Scan");
        }

        @ParameterizedTest
        @EnumSource(ScanType.class) // GH-90000
        @DisplayName("all scan types have non-null display names")
        void allScanTypesHaveNonNullDisplayNames(ScanType scanType) { // GH-90000
            assertThat(scanType.getDisplayName()) // GH-90000
                    .as("Display name for %s", scanType.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Description Tests")
    class DescriptionTests {

        @Test
        @DisplayName("SAST has correct description")
        void sastHasCorrectDescription() { // GH-90000
            assertThat(ScanType.SAST.getDescription()) // GH-90000
                    .isEqualTo("Analyzes source code for security vulnerabilities");
        }

        @Test
        @DisplayName("SCA has correct description")
        void scaHasCorrectDescription() { // GH-90000
            assertThat(ScanType.SCA.getDescription()) // GH-90000
                    .isEqualTo("Checks third-party dependencies for known vulnerabilities");
        }

        @Test
        @DisplayName("SECRET has correct description")
        void secretHasCorrectDescription() { // GH-90000
            assertThat(ScanType.SECRET.getDescription()) // GH-90000
                    .isEqualTo("Detects exposed secrets and credentials in code");
        }

        @ParameterizedTest
        @EnumSource(ScanType.class) // GH-90000
        @DisplayName("all scan types have non-null descriptions")
        void allScanTypesHaveNonNullDescriptions(ScanType scanType) { // GH-90000
            assertThat(scanType.getDescription()) // GH-90000
                    .as("Description for %s", scanType.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotEmpty(); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(ScanType.class) // GH-90000
        @DisplayName("all descriptions are meaningful (more than 10 chars)")
        void allDescriptionsAreMeaningful(ScanType scanType) { // GH-90000
            assertThat(scanType.getDescription().length()) // GH-90000
                    .as("Description length for %s", scanType.name()) // GH-90000
                    .isGreaterThan(10); // GH-90000
        }
    }

    @Nested
    @DisplayName("isComprehensive() Tests")
    class IsComprehensiveTests {

        @Test
        @DisplayName("FULL is comprehensive")
        void fullIsComprehensive() { // GH-90000
            assertThat(ScanType.FULL.isComprehensive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SAST is not comprehensive")
        void sastIsNotComprehensive() { // GH-90000
            assertThat(ScanType.SAST.isComprehensive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DAST is not comprehensive")
        void dastIsNotComprehensive() { // GH-90000
            assertThat(ScanType.DAST.isComprehensive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("QUICK is not comprehensive")
        void quickIsNotComprehensive() { // GH-90000
            assertThat(ScanType.QUICK.isComprehensive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("only FULL is comprehensive")
        void onlyFullIsComprehensive() { // GH-90000
            long comprehensiveCount = java.util.stream.Stream.of(ScanType.values()) // GH-90000
                    .filter(ScanType::isComprehensive) // GH-90000
                    .count(); // GH-90000
            assertThat(comprehensiveCount).isEqualTo(1); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(value = ScanType.class, names = {"SAST", "DAST", "SCA", "IAC", "CONTAINER", "SECRET", "QUICK", "INCREMENTAL"}) // GH-90000
        @DisplayName("non-FULL scan types are not comprehensive")
        void nonFullScanTypesAreNotComprehensive(ScanType scanType) { // GH-90000
            assertThat(scanType.isComprehensive()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Scan Type Categories Tests")
    class CategoryTests {

        @Test
        @DisplayName("static analysis scans include SAST")
        void staticAnalysisScansIncludeSast() { // GH-90000
            assertThat(ScanType.SAST.getDisplayName()).contains("Static");
        }

        @Test
        @DisplayName("dynamic analysis scans include DAST")
        void dynamicAnalysisScansIncludeDast() { // GH-90000
            assertThat(ScanType.DAST.getDisplayName()).contains("Dynamic");
        }

        @Test
        @DisplayName("dependency-related scans include SCA")
        void dependencyRelatedScansIncludeSca() { // GH-90000
            assertThat(ScanType.SCA.getDescription()).containsIgnoringCase("dependencies");
        }

        @Test
        @DisplayName("infrastructure scans include IAC")
        void infrastructureScansIncludeIac() { // GH-90000
            assertThat(ScanType.IAC.getDescription()).containsIgnoringCase("infrastructure");
        }

        @Test
        @DisplayName("container scans focus on images")
        void containerScansFocusOnImages() { // GH-90000
            assertThat(ScanType.CONTAINER.getDescription()).containsIgnoringCase("container");
        }

        @Test
        @DisplayName("secret detection focuses on credentials")
        void secretDetectionFocusesOnCredentials() { // GH-90000
            assertThat(ScanType.SECRET.getDescription()).containsIgnoringCase("secrets");
        }
    }

    @Nested
    @DisplayName("Enum Value Tests")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values")
        void enumHasExpectedNumberOfValues() { // GH-90000
            assertThat(ScanType.values()).hasSize(9); // GH-90000
        }

        @Test
        @DisplayName("enum values can be retrieved by name")
        void enumValuesCanBeRetrievedByName() { // GH-90000
            assertThat(ScanType.valueOf("SAST")).isEqualTo(ScanType.SAST);
            assertThat(ScanType.valueOf("DAST")).isEqualTo(ScanType.DAST);
            assertThat(ScanType.valueOf("SCA")).isEqualTo(ScanType.SCA);
            assertThat(ScanType.valueOf("IAC")).isEqualTo(ScanType.IAC);
            assertThat(ScanType.valueOf("CONTAINER")).isEqualTo(ScanType.CONTAINER);
            assertThat(ScanType.valueOf("SECRET")).isEqualTo(ScanType.SECRET);
            assertThat(ScanType.valueOf("FULL")).isEqualTo(ScanType.FULL);
            assertThat(ScanType.valueOf("QUICK")).isEqualTo(ScanType.QUICK);
            assertThat(ScanType.valueOf("INCREMENTAL")).isEqualTo(ScanType.INCREMENTAL);
        }
    }

    @Nested
    @DisplayName("Scan Speed/Scope Tests")
    class ScanSpeedScopeTests {

        @Test
        @DisplayName("QUICK is designed for fast scans")
        void quickIsDesignedForFastScans() { // GH-90000
            assertThat(ScanType.QUICK.getDisplayName()).containsIgnoringCase("Quick");
            assertThat(ScanType.QUICK.getDescription()).containsIgnoringCase("Fast");
        }

        @Test
        @DisplayName("INCREMENTAL only scans changes")
        void incrementalOnlyScansChanges() { // GH-90000
            assertThat(ScanType.INCREMENTAL.getDescription()).containsIgnoringCase("changed");
        }

        @Test
        @DisplayName("FULL includes all checks")
        void fullIncludesAllChecks() { // GH-90000
            assertThat(ScanType.FULL.getDescription()).containsIgnoringCase("Comprehensive");
        }
    }
}
