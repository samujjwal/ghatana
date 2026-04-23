/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Phase 3 — Security Hardening: Comprehensive tests for ComplianceEvidenceRegistry.
 * Covers control registration, framework queries, status summaries, compliance evaluation,
 * and report generation for SOC2, ISO 27001, HIPAA, and PCI DSS.
 */
package com.ghatana.datacloud.security;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link ComplianceEvidenceRegistry} verifying control registration,
 * lifecycle status tracking, per-framework queries, compliance evaluation correctness,
 * and report generation across all supported frameworks.
 *
 * @doc.type test
 * @doc.purpose Verify compliance evidence registry correctness for all frameworks and lifecycle states
 * @doc.layer security
 * @doc.pattern UnitTest
 */
@DisplayName("ComplianceEvidenceRegistry")
class ComplianceEvidenceRegistryTest {

    private ComplianceEvidenceRegistry registry;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ComplianceEvidenceRegistry.ComplianceControl control( // GH-90000
            String id,
            ComplianceEvidenceRegistry.ComplianceFramework framework,
            ComplianceEvidenceRegistry.ControlStatus status) {
        return new ComplianceEvidenceRegistry.ComplianceControl( // GH-90000
            id, framework, "Requirement: " + id, status,
            "Evidence for " + id, List.of(), Instant.now(), "team" // GH-90000
        );
    }

    @BeforeEach
    void setUp() { // GH-90000
        registry = new ComplianceEvidenceRegistry(); // GH-90000
    }

    // =========================================================================
    // Control Registration
    // =========================================================================

    @Nested
    @DisplayName("Control Registration")
    class ControlRegistration {

        @Test
        @DisplayName("registering a control increases the registry size by 1")
        void registerIncreasesSize() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));

            assertThat(registry.size()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("registered control is retrievable by its ID")
        void registeredControlRetrievableById() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));

            Optional<ComplianceEvidenceRegistry.ComplianceControl> found = registry.getControl("CC6.1");

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getControlId()).isEqualTo("CC6.1");
        }

        @Test
        @DisplayName("re-registering the same ID replaces the previous entry")
        void reRegisterReplacesPrevious() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));

            assertThat(registry.size()).isEqualTo(1); // GH-90000
            assertThat(registry.getControl("CC6.1").orElseThrow().getStatus())
                .isEqualTo(ComplianceEvidenceRegistry.ControlStatus.CERTIFIED); // GH-90000
        }

        @Test
        @DisplayName("registering null control throws NullPointerException")
        void registerNullThrows() { // GH-90000
            assertThatNullPointerException().isThrownBy(() -> registry.register(null)); // GH-90000
        }

        @Test
        @DisplayName("multiple distinct controls are stored independently")
        void multipleControlsStoredIndependently() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.DOCUMENTED));
            registry.register(control("A.9.1", ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.TESTED));

            assertThat(registry.size()).isEqualTo(3); // GH-90000
        }
    }

    // =========================================================================
    // Control Retrieval
    // =========================================================================

    @Nested
    @DisplayName("Control Retrieval")
    class ControlRetrieval {

        @Test
        @DisplayName("getControl returns empty for unknown ID")
        void unknownIdReturnsEmpty() { // GH-90000
            assertThat(registry.getControl("UNKNOWN")).isEmpty();
        }

        @Test
        @DisplayName("getControlsByFramework returns only controls for that framework")
        void getControlsByFrameworkFiltersCorrectly() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("A.9.1", ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.DOCUMENTED));

            List<ComplianceEvidenceRegistry.ComplianceControl> soc2 =
                registry.getControlsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.SOC2); // GH-90000

            assertThat(soc2).hasSize(2); // GH-90000
            assertThat(soc2).allMatch(c -> c.getFramework() == ComplianceEvidenceRegistry.ComplianceFramework.SOC2); // GH-90000
        }

        @Test
        @DisplayName("getControlsByFramework returns empty list when none registered for that framework")
        void emptyListForUnregisteredFramework() { // GH-90000
            assertThat(registry.getControlsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.HIPAA)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getControlsByStatus returns only controls in that status")
        void getControlsByStatusFiltersCorrectly() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));

            List<ComplianceEvidenceRegistry.ComplianceControl> certified =
                registry.getControlsByStatus(ComplianceEvidenceRegistry.ControlStatus.CERTIFIED); // GH-90000

            assertThat(certified).hasSize(1); // GH-90000
            assertThat(certified.get(0).getControlId()).isEqualTo("CC6.1");
        }

        @Test
        @DisplayName("getControlsByFramework with null throws NullPointerException")
        void nullFrameworkThrows() { // GH-90000
            assertThatNullPointerException().isThrownBy(() -> registry.getControlsByFramework(null)); // GH-90000
        }
    }

    // =========================================================================
    // Status Count Summaries
    // =========================================================================

    @Nested
    @DisplayName("Status Count Summary")
    class StatusCountSummary {

        @Test
        @DisplayName("getStatusCountsByFramework counts each status correctly")
        void statusCountsAreAccurate() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.3", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));
            registry.register(control("CC6.4", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.DOCUMENTED));

            Map<ComplianceEvidenceRegistry.ControlStatus, Long> counts =
                registry.getStatusCountsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.SOC2); // GH-90000

            assertThat(counts.get(ComplianceEvidenceRegistry.ControlStatus.CERTIFIED)).isEqualTo(2L); // GH-90000
            assertThat(counts.get(ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED)).isEqualTo(1L); // GH-90000
            assertThat(counts.get(ComplianceEvidenceRegistry.ControlStatus.DOCUMENTED)).isEqualTo(1L); // GH-90000
        }

        @Test
        @DisplayName("getStatusCountsByFramework returns empty map when framework has no controls")
        void emptyMapForEmptyFramework() { // GH-90000
            assertThat( // GH-90000
                registry.getStatusCountsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.PCI_DSS) // GH-90000
            ).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Framework Compliance Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Framework Compliance Evaluation")
    class FrameworkComplianceEvaluation {

        @Test
        @DisplayName("empty framework is not compliant")
        void emptyFrameworkIsNotCompliant() { // GH-90000
            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("framework with all CERTIFIED controls is compliant")
        void allCertifiedIsCompliant() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));

            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("framework with a mix of CERTIFIED and NOT_APPLICABLE controls is compliant")
        void certifiedAndNotApplicableIsCompliant() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.NOT_APPLICABLE));

            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("framework with one IMPLEMENTED (not CERTIFIED) control is not compliant")
        void oneImplementedMeansNotCompliant() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED)); // not yet certified

            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("framework with DOCUMENTED control is not compliant")
        void documentedControlMeansNotCompliant() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.DOCUMENTED));

            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("framework with TESTED control is not compliant (must reach CERTIFIED)")
        void testedControlMeansNotCompliant() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.TESTED));

            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("compliant framework is unaffected by another framework's non-certified controls")
        void frameworkComplianceIsIsolated() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            // ISO framework has only IMPLEMENTED control
            registry.register(control("A.9.1", ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));

            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).isTrue(); // GH-90000
            assertThat(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Report Generation
    // =========================================================================

    @Nested
    @DisplayName("Report Generation")
    class ReportGeneration {

        @Test
        @DisplayName("generateReport returns a non-null report with a recent timestamp")
        void reportHasRecentTimestamp() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            ComplianceEvidenceRegistry.ComplianceReport report = registry.generateReport(); // GH-90000
            Instant after = Instant.now(); // GH-90000

            assertThat(report.getGeneratedAt()) // GH-90000
                .isAfterOrEqualTo(before) // GH-90000
                .isBeforeOrEqualTo(after); // GH-90000
        }

        @Test
        @DisplayName("report contains entries for all four frameworks")
        void reportContainsAllFrameworks() { // GH-90000
            ComplianceEvidenceRegistry.ComplianceReport report = registry.generateReport(); // GH-90000

            assertThat(report.getFrameworkSummaries()).containsKeys( // GH-90000
                ComplianceEvidenceRegistry.ComplianceFramework.SOC2,
                ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001,
                ComplianceEvidenceRegistry.ComplianceFramework.HIPAA,
                ComplianceEvidenceRegistry.ComplianceFramework.PCI_DSS
            );
        }

        @Test
        @DisplayName("isFullyCompliant returns false when registry is empty")
        void emptyRegistryIsNotFullyCompliant() { // GH-90000
            assertThat(registry.generateReport().isFullyCompliant()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isFullyCompliant returns true when all frameworks have only CERTIFIED controls")
        void fullyCompliantWhenAllCertified() { // GH-90000
            for (ComplianceEvidenceRegistry.ComplianceFramework fw : // GH-90000
                    ComplianceEvidenceRegistry.ComplianceFramework.values()) { // GH-90000
                registry.register(control(fw.name() + "-1", fw, ComplianceEvidenceRegistry.ControlStatus.CERTIFIED)); // GH-90000
            }

            assertThat(registry.generateReport().isFullyCompliant()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("FrameworkSummary.getCertifiedCount is accurate")
        void frameworkSummaryCertifiedCountAccurate() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.2", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("CC6.3", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));

            ComplianceEvidenceRegistry.FrameworkSummary soc2 =
                registry.generateReport().getFrameworkSummaries() // GH-90000
                    .get(ComplianceEvidenceRegistry.ComplianceFramework.SOC2); // GH-90000

            assertThat(soc2.getCertifiedCount()).isEqualTo(2L); // GH-90000
            assertThat(soc2.getTotalControls()).isEqualTo(3L); // GH-90000
        }

        @Test
        @DisplayName("FrameworkSummary.isCompliant aligns with isFrameworkCompliant")
        void frameworkSummaryCompliantAligns() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));

            ComplianceEvidenceRegistry.ComplianceReport report = registry.generateReport(); // GH-90000
            ComplianceEvidenceRegistry.FrameworkSummary soc2Summary =
                report.getFrameworkSummaries().get(ComplianceEvidenceRegistry.ComplianceFramework.SOC2); // GH-90000

            assertThat(soc2Summary.isCompliant()) // GH-90000
                .isEqualTo(registry.isFrameworkCompliant(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)); // GH-90000
        }

        @Test
        @DisplayName("report toString contains useful information")
        void reportToStringIsInformative() { // GH-90000
            ComplianceEvidenceRegistry.ComplianceReport report = registry.generateReport(); // GH-90000

            assertThat(report.toString()) // GH-90000
                .contains("ComplianceReport")
                .contains("fullyCompliant");
        }
    }

    // =========================================================================
    // ComplianceControl Object Behaviour
    // =========================================================================

    @Nested
    @DisplayName("ComplianceControl Object")
    class ComplianceControlObject {

        @Test
        @DisplayName("evidenceLinks is an unmodifiable list copy")
        void evidenceLinksIsDefensiveCopy() { // GH-90000
            List<String> links = new java.util.ArrayList<>(List.of("https://link1", "https://link2")); // GH-90000
            ComplianceEvidenceRegistry.ComplianceControl ctrl =
                new ComplianceEvidenceRegistry.ComplianceControl( // GH-90000
                    "CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2,
                    "requirement", ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED,
                    "evidence", links, Instant.now(), "owner" // GH-90000
                );

            links.add("mutated-link");

            assertThat(ctrl.getEvidenceLinks()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("toString includes control ID, framework, and status")
        void toStringIsInformative() { // GH-90000
            String str = control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED).toString(); // GH-90000

            assertThat(str) // GH-90000
                .contains("CC6.1")
                .contains("SOC2")
                .contains("CERTIFIED");
        }

        @Test
        @DisplayName("two controls with same ID are equal regardless of other fields")
        void equalsBasedOnControlId() { // GH-90000
            ComplianceEvidenceRegistry.ComplianceControl c1 =
                control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                    ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED);
            ComplianceEvidenceRegistry.ComplianceControl c2 =
                control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.HIPAA, // GH-90000
                    ComplianceEvidenceRegistry.ControlStatus.CERTIFIED);

            assertThat(c1).isEqualTo(c2); // GH-90000
            assertThat(c1.hashCode()).isEqualTo(c2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("constructor requires non-null controlId")
        void nullControlIdThrows() { // GH-90000
            assertThatNullPointerException().isThrownBy(() -> // GH-90000
                new ComplianceEvidenceRegistry.ComplianceControl( // GH-90000
                    null, ComplianceEvidenceRegistry.ComplianceFramework.SOC2,
                    "req", ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED,
                    null, null, null, null
                )
            );
        }

        @Test
        @DisplayName("null optional fields default to safe empty values")
        void nullOptionalFieldsDefaultSafely() { // GH-90000
            ComplianceEvidenceRegistry.ComplianceControl ctrl =
                new ComplianceEvidenceRegistry.ComplianceControl( // GH-90000
                    "CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2,
                    "req", ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED,
                    null, null, null, null
                );

            assertThat(ctrl.getEvidenceDescription()).isNotNull().isEmpty(); // GH-90000
            assertThat(ctrl.getEvidenceLinks()).isNotNull().isEmpty(); // GH-90000
            assertThat(ctrl.getOwner()).isNotNull().isEmpty(); // GH-90000
            assertThat(ctrl.getLastVerified()).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("size returns 0 for a freshly created registry")
        void emptyRegistryHasSizeZero() { // GH-90000
            assertThat(registry.size()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("controls for different frameworks are independently maintained")
        void multiFrameworkRegistrationIsIndependent() { // GH-90000
            registry.register(control("SOC2-1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.CERTIFIED));
            registry.register(control("ISO-1", ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.TESTED));
            registry.register(control("HIPAA-1", ComplianceEvidenceRegistry.ComplianceFramework.HIPAA, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.DOCUMENTED));
            registry.register(control("PCI-1", ComplianceEvidenceRegistry.ComplianceFramework.PCI_DSS, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.IMPLEMENTED));

            assertThat(registry.getControlsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.SOC2)).hasSize(1); // GH-90000
            assertThat(registry.getControlsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.ISO_27001)).hasSize(1); // GH-90000
            assertThat(registry.getControlsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.HIPAA)).hasSize(1); // GH-90000
            assertThat(registry.getControlsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.PCI_DSS)).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("NOT_APPLICABLE controls are included in status counts")
        void notApplicableIncludedInCounts() { // GH-90000
            registry.register(control("CC6.1", ComplianceEvidenceRegistry.ComplianceFramework.SOC2, // GH-90000
                ComplianceEvidenceRegistry.ControlStatus.NOT_APPLICABLE));

            Map<ComplianceEvidenceRegistry.ControlStatus, Long> counts =
                registry.getStatusCountsByFramework(ComplianceEvidenceRegistry.ComplianceFramework.SOC2); // GH-90000

            assertThat(counts.get(ComplianceEvidenceRegistry.ControlStatus.NOT_APPLICABLE)).isEqualTo(1L); // GH-90000
        }
    }
}
