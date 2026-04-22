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
@DisplayName("ScanType Enum Tests [GH-90000]")
class ScanTypeTest {

    @Nested
    @DisplayName("Display Name Tests [GH-90000]")
    class DisplayNameTests {

        @Test
        @DisplayName("SAST has correct display name [GH-90000]")
        void sastHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.SAST.getDisplayName()).isEqualTo("Static Analysis [GH-90000]");
        }

        @Test
        @DisplayName("DAST has correct display name [GH-90000]")
        void dastHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.DAST.getDisplayName()).isEqualTo("Dynamic Analysis [GH-90000]");
        }

        @Test
        @DisplayName("SCA has correct display name [GH-90000]")
        void scaHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.SCA.getDisplayName()).isEqualTo("Dependency Scan [GH-90000]");
        }

        @Test
        @DisplayName("IAC has correct display name [GH-90000]")
        void iacHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.IAC.getDisplayName()).isEqualTo("IaC Scan [GH-90000]");
        }

        @Test
        @DisplayName("CONTAINER has correct display name [GH-90000]")
        void containerHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.CONTAINER.getDisplayName()).isEqualTo("Container Scan [GH-90000]");
        }

        @Test
        @DisplayName("SECRET has correct display name [GH-90000]")
        void secretHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.SECRET.getDisplayName()).isEqualTo("Secret Detection [GH-90000]");
        }

        @Test
        @DisplayName("FULL has correct display name [GH-90000]")
        void fullHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.FULL.getDisplayName()).isEqualTo("Full Scan [GH-90000]");
        }

        @Test
        @DisplayName("QUICK has correct display name [GH-90000]")
        void quickHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.QUICK.getDisplayName()).isEqualTo("Quick Scan [GH-90000]");
        }

        @Test
        @DisplayName("INCREMENTAL has correct display name [GH-90000]")
        void incrementalHasCorrectDisplayName() { // GH-90000
            assertThat(ScanType.INCREMENTAL.getDisplayName()).isEqualTo("Incremental Scan [GH-90000]");
        }

        @ParameterizedTest
        @EnumSource(ScanType.class) // GH-90000
        @DisplayName("all scan types have non-null display names [GH-90000]")
        void allScanTypesHaveNonNullDisplayNames(ScanType scanType) { // GH-90000
            assertThat(scanType.getDisplayName()) // GH-90000
                    .as("Display name for %s", scanType.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Description Tests [GH-90000]")
    class DescriptionTests {

        @Test
        @DisplayName("SAST has correct description [GH-90000]")
        void sastHasCorrectDescription() { // GH-90000
            assertThat(ScanType.SAST.getDescription()) // GH-90000
                    .isEqualTo("Analyzes source code for security vulnerabilities [GH-90000]");
        }

        @Test
        @DisplayName("SCA has correct description [GH-90000]")
        void scaHasCorrectDescription() { // GH-90000
            assertThat(ScanType.SCA.getDescription()) // GH-90000
                    .isEqualTo("Checks third-party dependencies for known vulnerabilities [GH-90000]");
        }

        @Test
        @DisplayName("SECRET has correct description [GH-90000]")
        void secretHasCorrectDescription() { // GH-90000
            assertThat(ScanType.SECRET.getDescription()) // GH-90000
                    .isEqualTo("Detects exposed secrets and credentials in code [GH-90000]");
        }

        @ParameterizedTest
        @EnumSource(ScanType.class) // GH-90000
        @DisplayName("all scan types have non-null descriptions [GH-90000]")
        void allScanTypesHaveNonNullDescriptions(ScanType scanType) { // GH-90000
            assertThat(scanType.getDescription()) // GH-90000
                    .as("Description for %s", scanType.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotEmpty(); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(ScanType.class) // GH-90000
        @DisplayName("all descriptions are meaningful (more than 10 chars) [GH-90000]")
        void allDescriptionsAreMeaningful(ScanType scanType) { // GH-90000
            assertThat(scanType.getDescription().length()) // GH-90000
                    .as("Description length for %s", scanType.name()) // GH-90000
                    .isGreaterThan(10); // GH-90000
        }
    }

    @Nested
    @DisplayName("isComprehensive() Tests [GH-90000]")
    class IsComprehensiveTests {

        @Test
        @DisplayName("FULL is comprehensive [GH-90000]")
        void fullIsComprehensive() { // GH-90000
            assertThat(ScanType.FULL.isComprehensive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SAST is not comprehensive [GH-90000]")
        void sastIsNotComprehensive() { // GH-90000
            assertThat(ScanType.SAST.isComprehensive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DAST is not comprehensive [GH-90000]")
        void dastIsNotComprehensive() { // GH-90000
            assertThat(ScanType.DAST.isComprehensive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("QUICK is not comprehensive [GH-90000]")
        void quickIsNotComprehensive() { // GH-90000
            assertThat(ScanType.QUICK.isComprehensive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("only FULL is comprehensive [GH-90000]")
        void onlyFullIsComprehensive() { // GH-90000
            long comprehensiveCount = java.util.stream.Stream.of(ScanType.values()) // GH-90000
                    .filter(ScanType::isComprehensive) // GH-90000
                    .count(); // GH-90000
            assertThat(comprehensiveCount).isEqualTo(1); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(value = ScanType.class, names = {"SAST", "DAST", "SCA", "IAC", "CONTAINER", "SECRET", "QUICK", "INCREMENTAL"}) // GH-90000
        @DisplayName("non-FULL scan types are not comprehensive [GH-90000]")
        void nonFullScanTypesAreNotComprehensive(ScanType scanType) { // GH-90000
            assertThat(scanType.isComprehensive()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Scan Type Categories Tests [GH-90000]")
    class CategoryTests {

        @Test
        @DisplayName("static analysis scans include SAST [GH-90000]")
        void staticAnalysisScansIncludeSast() { // GH-90000
            assertThat(ScanType.SAST.getDisplayName()).contains("Static [GH-90000]");
        }

        @Test
        @DisplayName("dynamic analysis scans include DAST [GH-90000]")
        void dynamicAnalysisScansIncludeDast() { // GH-90000
            assertThat(ScanType.DAST.getDisplayName()).contains("Dynamic [GH-90000]");
        }

        @Test
        @DisplayName("dependency-related scans include SCA [GH-90000]")
        void dependencyRelatedScansIncludeSca() { // GH-90000
            assertThat(ScanType.SCA.getDescription()).containsIgnoringCase("dependencies [GH-90000]");
        }

        @Test
        @DisplayName("infrastructure scans include IAC [GH-90000]")
        void infrastructureScansIncludeIac() { // GH-90000
            assertThat(ScanType.IAC.getDescription()).containsIgnoringCase("infrastructure [GH-90000]");
        }

        @Test
        @DisplayName("container scans focus on images [GH-90000]")
        void containerScansFocusOnImages() { // GH-90000
            assertThat(ScanType.CONTAINER.getDescription()).containsIgnoringCase("container [GH-90000]");
        }

        @Test
        @DisplayName("secret detection focuses on credentials [GH-90000]")
        void secretDetectionFocusesOnCredentials() { // GH-90000
            assertThat(ScanType.SECRET.getDescription()).containsIgnoringCase("secrets [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Enum Value Tests [GH-90000]")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values [GH-90000]")
        void enumHasExpectedNumberOfValues() { // GH-90000
            assertThat(ScanType.values()).hasSize(9); // GH-90000
        }

        @Test
        @DisplayName("enum values can be retrieved by name [GH-90000]")
        void enumValuesCanBeRetrievedByName() { // GH-90000
            assertThat(ScanType.valueOf("SAST [GH-90000]")).isEqualTo(ScanType.SAST);
            assertThat(ScanType.valueOf("DAST [GH-90000]")).isEqualTo(ScanType.DAST);
            assertThat(ScanType.valueOf("SCA [GH-90000]")).isEqualTo(ScanType.SCA);
            assertThat(ScanType.valueOf("IAC [GH-90000]")).isEqualTo(ScanType.IAC);
            assertThat(ScanType.valueOf("CONTAINER [GH-90000]")).isEqualTo(ScanType.CONTAINER);
            assertThat(ScanType.valueOf("SECRET [GH-90000]")).isEqualTo(ScanType.SECRET);
            assertThat(ScanType.valueOf("FULL [GH-90000]")).isEqualTo(ScanType.FULL);
            assertThat(ScanType.valueOf("QUICK [GH-90000]")).isEqualTo(ScanType.QUICK);
            assertThat(ScanType.valueOf("INCREMENTAL [GH-90000]")).isEqualTo(ScanType.INCREMENTAL);
        }
    }

    @Nested
    @DisplayName("Scan Speed/Scope Tests [GH-90000]")
    class ScanSpeedScopeTests {

        @Test
        @DisplayName("QUICK is designed for fast scans [GH-90000]")
        void quickIsDesignedForFastScans() { // GH-90000
            assertThat(ScanType.QUICK.getDisplayName()).containsIgnoringCase("Quick [GH-90000]");
            assertThat(ScanType.QUICK.getDescription()).containsIgnoringCase("Fast [GH-90000]");
        }

        @Test
        @DisplayName("INCREMENTAL only scans changes [GH-90000]")
        void incrementalOnlyScansChanges() { // GH-90000
            assertThat(ScanType.INCREMENTAL.getDescription()).containsIgnoringCase("changed [GH-90000]");
        }

        @Test
        @DisplayName("FULL includes all checks [GH-90000]")
        void fullIncludesAllChecks() { // GH-90000
            assertThat(ScanType.FULL.getDescription()).containsIgnoringCase("Comprehensive [GH-90000]");
        }
    }
}
