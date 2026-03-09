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
        void sastHasCorrectDisplayName() {
            assertThat(ScanType.SAST.getDisplayName()).isEqualTo("Static Analysis");
        }

        @Test
        @DisplayName("DAST has correct display name")
        void dastHasCorrectDisplayName() {
            assertThat(ScanType.DAST.getDisplayName()).isEqualTo("Dynamic Analysis");
        }

        @Test
        @DisplayName("SCA has correct display name")
        void scaHasCorrectDisplayName() {
            assertThat(ScanType.SCA.getDisplayName()).isEqualTo("Dependency Scan");
        }

        @Test
        @DisplayName("IAC has correct display name")
        void iacHasCorrectDisplayName() {
            assertThat(ScanType.IAC.getDisplayName()).isEqualTo("IaC Scan");
        }

        @Test
        @DisplayName("CONTAINER has correct display name")
        void containerHasCorrectDisplayName() {
            assertThat(ScanType.CONTAINER.getDisplayName()).isEqualTo("Container Scan");
        }

        @Test
        @DisplayName("SECRET has correct display name")
        void secretHasCorrectDisplayName() {
            assertThat(ScanType.SECRET.getDisplayName()).isEqualTo("Secret Detection");
        }

        @Test
        @DisplayName("FULL has correct display name")
        void fullHasCorrectDisplayName() {
            assertThat(ScanType.FULL.getDisplayName()).isEqualTo("Full Scan");
        }

        @Test
        @DisplayName("QUICK has correct display name")
        void quickHasCorrectDisplayName() {
            assertThat(ScanType.QUICK.getDisplayName()).isEqualTo("Quick Scan");
        }

        @Test
        @DisplayName("INCREMENTAL has correct display name")
        void incrementalHasCorrectDisplayName() {
            assertThat(ScanType.INCREMENTAL.getDisplayName()).isEqualTo("Incremental Scan");
        }

        @ParameterizedTest
        @EnumSource(ScanType.class)
        @DisplayName("all scan types have non-null display names")
        void allScanTypesHaveNonNullDisplayNames(ScanType scanType) {
            assertThat(scanType.getDisplayName())
                    .as("Display name for %s", scanType.name())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Description Tests")
    class DescriptionTests {

        @Test
        @DisplayName("SAST has correct description")
        void sastHasCorrectDescription() {
            assertThat(ScanType.SAST.getDescription())
                    .isEqualTo("Analyzes source code for security vulnerabilities");
        }

        @Test
        @DisplayName("SCA has correct description")
        void scaHasCorrectDescription() {
            assertThat(ScanType.SCA.getDescription())
                    .isEqualTo("Checks third-party dependencies for known vulnerabilities");
        }

        @Test
        @DisplayName("SECRET has correct description")
        void secretHasCorrectDescription() {
            assertThat(ScanType.SECRET.getDescription())
                    .isEqualTo("Detects exposed secrets and credentials in code");
        }

        @ParameterizedTest
        @EnumSource(ScanType.class)
        @DisplayName("all scan types have non-null descriptions")
        void allScanTypesHaveNonNullDescriptions(ScanType scanType) {
            assertThat(scanType.getDescription())
                    .as("Description for %s", scanType.name())
                    .isNotNull()
                    .isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(ScanType.class)
        @DisplayName("all descriptions are meaningful (more than 10 chars)")
        void allDescriptionsAreMeaningful(ScanType scanType) {
            assertThat(scanType.getDescription().length())
                    .as("Description length for %s", scanType.name())
                    .isGreaterThan(10);
        }
    }

    @Nested
    @DisplayName("isComprehensive() Tests")
    class IsComprehensiveTests {

        @Test
        @DisplayName("FULL is comprehensive")
        void fullIsComprehensive() {
            assertThat(ScanType.FULL.isComprehensive()).isTrue();
        }

        @Test
        @DisplayName("SAST is not comprehensive")
        void sastIsNotComprehensive() {
            assertThat(ScanType.SAST.isComprehensive()).isFalse();
        }

        @Test
        @DisplayName("DAST is not comprehensive")
        void dastIsNotComprehensive() {
            assertThat(ScanType.DAST.isComprehensive()).isFalse();
        }

        @Test
        @DisplayName("QUICK is not comprehensive")
        void quickIsNotComprehensive() {
            assertThat(ScanType.QUICK.isComprehensive()).isFalse();
        }

        @Test
        @DisplayName("only FULL is comprehensive")
        void onlyFullIsComprehensive() {
            long comprehensiveCount = java.util.stream.Stream.of(ScanType.values())
                    .filter(ScanType::isComprehensive)
                    .count();
            assertThat(comprehensiveCount).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(value = ScanType.class, names = {"SAST", "DAST", "SCA", "IAC", "CONTAINER", "SECRET", "QUICK", "INCREMENTAL"})
        @DisplayName("non-FULL scan types are not comprehensive")
        void nonFullScanTypesAreNotComprehensive(ScanType scanType) {
            assertThat(scanType.isComprehensive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scan Type Categories Tests")
    class CategoryTests {

        @Test
        @DisplayName("static analysis scans include SAST")
        void staticAnalysisScansIncludeSast() {
            assertThat(ScanType.SAST.getDisplayName()).contains("Static");
        }

        @Test
        @DisplayName("dynamic analysis scans include DAST")
        void dynamicAnalysisScansIncludeDast() {
            assertThat(ScanType.DAST.getDisplayName()).contains("Dynamic");
        }

        @Test
        @DisplayName("dependency-related scans include SCA")
        void dependencyRelatedScansIncludeSca() {
            assertThat(ScanType.SCA.getDescription()).containsIgnoringCase("dependencies");
        }

        @Test
        @DisplayName("infrastructure scans include IAC")
        void infrastructureScansIncludeIac() {
            assertThat(ScanType.IAC.getDescription()).containsIgnoringCase("infrastructure");
        }

        @Test
        @DisplayName("container scans focus on images")
        void containerScansFocusOnImages() {
            assertThat(ScanType.CONTAINER.getDescription()).containsIgnoringCase("container");
        }

        @Test
        @DisplayName("secret detection focuses on credentials")
        void secretDetectionFocusesOnCredentials() {
            assertThat(ScanType.SECRET.getDescription()).containsIgnoringCase("secrets");
        }
    }

    @Nested
    @DisplayName("Enum Value Tests")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values")
        void enumHasExpectedNumberOfValues() {
            assertThat(ScanType.values()).hasSize(9);
        }

        @Test
        @DisplayName("enum values can be retrieved by name")
        void enumValuesCanBeRetrievedByName() {
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
        void quickIsDesignedForFastScans() {
            assertThat(ScanType.QUICK.getDisplayName()).containsIgnoringCase("Quick");
            assertThat(ScanType.QUICK.getDescription()).containsIgnoringCase("Fast");
        }

        @Test
        @DisplayName("INCREMENTAL only scans changes")
        void incrementalOnlyScansChanges() {
            assertThat(ScanType.INCREMENTAL.getDescription()).containsIgnoringCase("changed");
        }

        @Test
        @DisplayName("FULL includes all checks")
        void fullIncludesAllChecks() {
            assertThat(ScanType.FULL.getDescription()).containsIgnoringCase("Comprehensive");
        }
    }
}
