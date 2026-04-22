/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for policy recommendation service.
 *
 * @doc.type    class
 * @doc.purpose Tests for policy recommendation engine including violation analysis, pattern analysis, and compliance gap detection
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("PolicyRecommendationServiceTest [GH-90000]")
@Tag("governance [GH-90000]")
class PolicyRecommendationServiceTest {

    private PolicyRecommendationService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new PolicyRecommendationService(); // GH-90000
    }

    // ─── Violation-based recommendations ─────────────────────────────────────────

    @Test
    @DisplayName("generates recommendation for high violation rate [GH-90000]")
    void generatesRecommendationForHighViolationRate() { // GH-90000
        List<PolicyViolation> violations = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 15; i++) { // GH-90000
            violations.add(new PolicyViolation( // GH-90000
                    "v" + i, "policy-1", "tenant-1", "user-1",
                    "READ", "resource-1", "ACCESS_DENIED",
                    Instant.now(), Map.of() // GH-90000
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); // GH-90000

        assertThat(recommendations).hasSize(1); // GH-90000
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); // GH-90000
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_MODIFICATION); // GH-90000
        assertThat(rec.priority()).isEqualTo(PolicyRecommendationService.RecommendationPriority.HIGH); // GH-90000
        assertThat(rec.description()).contains("15 violations [GH-90000]");
    }

    @Test
    @DisplayName("generates recommendation for repeated user violations [GH-90000]")
    void generatesRecommendationForUserViolations() { // GH-90000
        List<PolicyViolation> violations = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 6; i++) { // GH-90000
            violations.add(new PolicyViolation( // GH-90000
                    "v" + i, "policy-1", "tenant-1", "problematic-user",
                    "WRITE", "resource-1", "ACCESS_DENIED",
                    Instant.now(), Map.of() // GH-90000
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); // GH-90000

        assertThat(recommendations).hasSize(1); // GH-90000
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); // GH-90000
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.ACCESS_CONTROL_IMPROVEMENT); // GH-90000
        assertThat(rec.description()).contains("problematic-user [GH-90000]");
    }

    @Test
    @DisplayName("no recommendation for low violation count [GH-90000]")
    void noRecommendationForLowViolationCount() { // GH-90000
        List<PolicyViolation> violations = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            violations.add(new PolicyViolation( // GH-90000
                    "v" + i, "policy-1", "tenant-1", "user-1",
                    "READ", "resource-1", "ACCESS_DENIED",
                    Instant.now(), Map.of() // GH-90000
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); // GH-90000

        assertThat(recommendations).isEmpty(); // GH-90000
    }

    // ─── Access pattern recommendations ─────────────────────────────────────────

    @Test
    @DisplayName("generates recommendation for frequently accessed resources [GH-90000]")
    void generatesRecommendationForFrequentAccess() { // GH-90000
        List<PolicyRecommendationService.AccessPattern> patterns = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            patterns.add(new PolicyRecommendationService.AccessPattern( // GH-90000
                    "user-1", "tenant-1", "resource-" + i, "READ",
                    100, Instant.now(), true // GH-90000
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generatePatternRecommendations(patterns); // GH-90000

        assertThat(recommendations).hasSize(1); // GH-90000
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); // GH-90000
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_CREATION); // GH-90000
        assertThat(rec.description()).contains("frequently accessed [GH-90000]");
    }

    @Test
    @DisplayName("generates recommendation for orphaned access patterns [GH-90000]")
    void generatesRecommendationForOrphanedPatterns() { // GH-90000
        List<PolicyRecommendationService.AccessPattern> patterns = new ArrayList<>(); // GH-90000
        patterns.add(new PolicyRecommendationService.AccessPattern( // GH-90000
                "user-1", "tenant-1", "old-resource", "READ",
                50, Instant.now().minus(java.time.Duration.ofDays(100)), false // GH-90000
        ));

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generatePatternRecommendations(patterns); // GH-90000

        assertThat(recommendations).hasSize(1); // GH-90000
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); // GH-90000
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_DELETION); // GH-90000
        assertThat(rec.description()).contains("90 days [GH-90000]");
    }

    // ─── Compliance gap recommendations ─────────────────────────────────────────

    @Test
    @DisplayName("generates recommendation for missing required policy types [GH-90000]")
    void generatesRecommendationForMissingPolicyTypes() { // GH-90000
        List<PolicyService.Policy> currentPolicies = List.of( // GH-90000
                new PolicyService.Policy( // GH-90000
                        "p1", "Data Retention", "desc", "tenant-1",
                        PolicyService.PolicyType.DATA_RETENTION,
                        List.of(), true, 1, Instant.now(), Instant.now() // GH-90000
                )
        );

        PolicyRecommendationService.ComplianceRequirements requirements =
                new PolicyRecommendationService.ComplianceRequirements( // GH-90000
                        Set.of("DATA_RETENTION", "ACCESS_CONTROL"), // GH-90000
                        Map.of(), // GH-90000
                        Set.of() // GH-90000
                );

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateComplianceRecommendations(currentPolicies, requirements); // GH-90000

        assertThat(recommendations).hasSize(1); // GH-90000
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); // GH-90000
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.COMPLIANCE_REMEDIATION); // GH-90000
        assertThat(rec.priority()).isEqualTo(PolicyRecommendationService.RecommendationPriority.CRITICAL); // GH-90000
        assertThat(rec.description()).contains("ACCESS_CONTROL [GH-90000]");
    }

    @Test
    @DisplayName("generates recommendation for disabled policies [GH-90000]")
    void generatesRecommendationForDisabledPolicies() { // GH-90000
        List<PolicyService.Policy> currentPolicies = List.of( // GH-90000
                new PolicyService.Policy( // GH-90000
                        "p1", "Data Retention", "desc", "tenant-1",
                        PolicyService.PolicyType.DATA_RETENTION,
                        List.of(), false, 1, Instant.now(), Instant.now() // GH-90000
                ),
                new PolicyService.Policy( // GH-90000
                        "p2", "Access Control", "desc", "tenant-1",
                        PolicyService.PolicyType.ACCESS_CONTROL,
                        List.of(), false, 2, Instant.now(), Instant.now() // GH-90000
                )
        );

        PolicyRecommendationService.ComplianceRequirements requirements =
                new PolicyRecommendationService.ComplianceRequirements( // GH-90000
                        Set.of("DATA_RETENTION [GH-90000]"),
                        Map.of(), // GH-90000
                        Set.of() // GH-90000
                );

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateComplianceRecommendations(currentPolicies, requirements); // GH-90000

        assertThat(recommendations).hasSize(1); // GH-90000
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); // GH-90000
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.PRIORITY_ADJUSTMENT); // GH-90000
        assertThat(rec.description()).contains("disabled [GH-90000]");
    }

    @Test
    @DisplayName("no recommendation when compliance is met [GH-90000]")
    void noRecommendationWhenComplianceMet() { // GH-90000
        List<PolicyService.Policy> currentPolicies = List.of( // GH-90000
                new PolicyService.Policy( // GH-90000
                        "p1", "Data Retention", "desc", "tenant-1",
                        PolicyService.PolicyType.DATA_RETENTION,
                        List.of(), true, 1, Instant.now(), Instant.now() // GH-90000
                ),
                new PolicyService.Policy( // GH-90000
                        "p2", "Access Control", "desc", "tenant-1",
                        PolicyService.PolicyType.ACCESS_CONTROL,
                        List.of(), true, 2, Instant.now(), Instant.now() // GH-90000
                )
        );

        PolicyRecommendationService.ComplianceRequirements requirements =
                new PolicyRecommendationService.ComplianceRequirements( // GH-90000
                        Set.of("DATA_RETENTION", "ACCESS_CONTROL"), // GH-90000
                        Map.of(), // GH-90000
                        Set.of() // GH-90000
                );

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateComplianceRecommendations(currentPolicies, requirements); // GH-90000

        assertThat(recommendations).isEmpty(); // GH-90000
    }

    // ─── Recommendation ordering ─────────────────────────────────────────────────

    @Test
    @DisplayName("recommendations are sorted by priority descending [GH-90000]")
    void recommendationsAreSortedByPriority() { // GH-90000
        List<PolicyViolation> violations = new ArrayList<>(); // GH-90000
        // Generate 15 violations for HIGH priority
        for (int i = 0; i < 15; i++) { // GH-90000
            violations.add(new PolicyViolation( // GH-90000
                    "v" + i, "policy-1", "tenant-1", "user-1",
                    "READ", "resource-1", "TYPE_A",
                    Instant.now(), Map.of() // GH-90000
            ));
        }
        // Generate 6 user violations for MEDIUM priority
        for (int i = 0; i < 6; i++) { // GH-90000
            violations.add(new PolicyViolation( // GH-90000
                    "v" + (15 + i), "policy-1", "tenant-1", "user-2", // GH-90000
                    "WRITE", "resource-1", "TYPE_B",
                    Instant.now(), Map.of() // GH-90000
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); // GH-90000

        assertThat(recommendations).hasSize(2); // GH-90000
        assertThat(recommendations.get(0).priority()) // GH-90000
                .isGreaterThanOrEqualTo(recommendations.get(1).priority()); // GH-90000
    }

    // ─── Recommendation record validation ───────────────────────────────────────

    @Test
    @DisplayName("recommendation record has required fields [GH-90000]")
    void recommendationHasRequiredFields() { // GH-90000
        PolicyRecommendationService.Recommendation rec = new PolicyRecommendationService.Recommendation( // GH-90000
                "id-1",
                PolicyRecommendationService.RecommendationType.POLICY_CREATION,
                "Test Title",
                "Test Description",
                PolicyRecommendationService.RecommendationPriority.HIGH,
                "policy-1",
                Map.of("key", "value"), // GH-90000
                Instant.now() // GH-90000
        );

        assertThat(rec.id()).isEqualTo("id-1 [GH-90000]");
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_CREATION); // GH-90000
        assertThat(rec.title()).isEqualTo("Test Title [GH-90000]");
        assertThat(rec.description()).isEqualTo("Test Description [GH-90000]");
        assertThat(rec.priority()).isEqualTo(PolicyRecommendationService.RecommendationPriority.HIGH); // GH-90000
        assertThat(rec.suggestedPolicyId()).isEqualTo("policy-1 [GH-90000]");
        assertThat(rec.parameters()).containsEntry("key", "value"); // GH-90000
    }

    @Test
    @DisplayName("priority levels are correctly ordered [GH-90000]")
    void priorityLevelsAreOrdered() { // GH-90000
        assertThat(PolicyRecommendationService.RecommendationPriority.CRITICAL.level()) // GH-90000
                .isGreaterThan(PolicyRecommendationService.RecommendationPriority.HIGH.level()); // GH-90000
        assertThat(PolicyRecommendationService.RecommendationPriority.HIGH.level()) // GH-90000
                .isGreaterThan(PolicyRecommendationService.RecommendationPriority.MEDIUM.level()); // GH-90000
        assertThat(PolicyRecommendationService.RecommendationPriority.MEDIUM.level()) // GH-90000
                .isGreaterThan(PolicyRecommendationService.RecommendationPriority.LOW.level()); // GH-90000
    }
}
