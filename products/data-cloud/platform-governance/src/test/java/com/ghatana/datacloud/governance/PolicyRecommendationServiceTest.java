/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("PolicyRecommendationServiceTest")
@Tag("governance")
class PolicyRecommendationServiceTest {

    private PolicyRecommendationService service;

    @BeforeEach
    void setUp() { 
        service = new PolicyRecommendationService(); 
    }

    // ─── Violation-based recommendations ─────────────────────────────────────────

    @Test
    @DisplayName("generates recommendation for high violation rate")
    void generatesRecommendationForHighViolationRate() { 
        List<PolicyViolation> violations = new ArrayList<>(); 
        for (int i = 0; i < 15; i++) { 
            violations.add(new PolicyViolation( 
                    "v" + i, "policy-1", "tenant-1", "user-1",
                    "READ", "resource-1", "ACCESS_DENIED",
                    Instant.now(), Map.of() 
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); 

        assertThat(recommendations).hasSize(1); 
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); 
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_MODIFICATION); 
        assertThat(rec.priority()).isEqualTo(PolicyRecommendationService.RecommendationPriority.HIGH); 
        assertThat(rec.description()).contains("15 violations");
    }

    @Test
    @DisplayName("generates recommendation for repeated user violations")
    void generatesRecommendationForUserViolations() { 
        List<PolicyViolation> violations = new ArrayList<>(); 
        for (int i = 0; i < 6; i++) { 
            violations.add(new PolicyViolation( 
                    "v" + i, "policy-1", "tenant-1", "problematic-user",
                    "WRITE", "resource-1", "ACCESS_DENIED",
                    Instant.now(), Map.of() 
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); 

        assertThat(recommendations).hasSize(1); 
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); 
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.ACCESS_CONTROL_IMPROVEMENT); 
        assertThat(rec.description()).contains("problematic-user");
    }

    @Test
    @DisplayName("no recommendation for low violation count")
    void noRecommendationForLowViolationCount() { 
        List<PolicyViolation> violations = new ArrayList<>(); 
        for (int i = 0; i < 5; i++) { 
            violations.add(new PolicyViolation( 
                    "v" + i, "policy-1", "tenant-1", "user-1",
                    "READ", "resource-1", "ACCESS_DENIED",
                    Instant.now(), Map.of() 
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); 

        assertThat(recommendations).isEmpty(); 
    }

    // ─── Access pattern recommendations ─────────────────────────────────────────

    @Test
    @DisplayName("generates recommendation for frequently accessed resources")
    void generatesRecommendationForFrequentAccess() { 
        List<PolicyRecommendationService.AccessPattern> patterns = new ArrayList<>(); 
        for (int i = 0; i < 10; i++) { 
            patterns.add(new PolicyRecommendationService.AccessPattern( 
                    "user-1", "tenant-1", "resource-" + i, "READ",
                    100, Instant.now(), true 
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generatePatternRecommendations(patterns); 

        assertThat(recommendations).hasSize(1); 
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); 
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_CREATION); 
        assertThat(rec.description()).contains("frequently accessed");
    }

    @Test
    @DisplayName("generates recommendation for orphaned access patterns")
    void generatesRecommendationForOrphanedPatterns() { 
        List<PolicyRecommendationService.AccessPattern> patterns = new ArrayList<>(); 
        patterns.add(new PolicyRecommendationService.AccessPattern( 
                "user-1", "tenant-1", "old-resource", "READ",
                50, Instant.now().minus(java.time.Duration.ofDays(100)), false 
        ));

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generatePatternRecommendations(patterns); 

        assertThat(recommendations).hasSize(1); 
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); 
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_DELETION); 
        assertThat(rec.description()).contains("90 days");
    }

    // ─── Compliance gap recommendations ─────────────────────────────────────────

    @Test
    @DisplayName("generates recommendation for missing required policy types")
    void generatesRecommendationForMissingPolicyTypes() { 
        List<PolicyService.Policy> currentPolicies = List.of( 
                new PolicyService.Policy( 
                        "p1", "Data Retention", "desc", "tenant-1",
                        PolicyService.PolicyType.DATA_RETENTION,
                        List.of(), true, 1, Instant.now(), Instant.now() 
                )
        );

        PolicyRecommendationService.ComplianceRequirements requirements =
                new PolicyRecommendationService.ComplianceRequirements( 
                        Set.of("DATA_RETENTION", "ACCESS_CONTROL"), 
                        Map.of(), 
                        Set.of() 
                );

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateComplianceRecommendations(currentPolicies, requirements); 

        assertThat(recommendations).hasSize(1); 
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); 
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.COMPLIANCE_REMEDIATION); 
        assertThat(rec.priority()).isEqualTo(PolicyRecommendationService.RecommendationPriority.CRITICAL); 
        assertThat(rec.description()).contains("ACCESS_CONTROL");
    }

    @Test
    @DisplayName("generates recommendation for disabled policies")
    void generatesRecommendationForDisabledPolicies() { 
        List<PolicyService.Policy> currentPolicies = List.of( 
                new PolicyService.Policy( 
                        "p1", "Data Retention", "desc", "tenant-1",
                        PolicyService.PolicyType.DATA_RETENTION,
                        List.of(), false, 1, Instant.now(), Instant.now() 
                ),
                new PolicyService.Policy( 
                        "p2", "Access Control", "desc", "tenant-1",
                        PolicyService.PolicyType.ACCESS_CONTROL,
                        List.of(), false, 2, Instant.now(), Instant.now() 
                )
        );

        PolicyRecommendationService.ComplianceRequirements requirements =
                new PolicyRecommendationService.ComplianceRequirements( 
                        Set.of("DATA_RETENTION"),
                        Map.of(), 
                        Set.of() 
                );

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateComplianceRecommendations(currentPolicies, requirements); 

        assertThat(recommendations).hasSize(1); 
        PolicyRecommendationService.Recommendation rec = recommendations.getFirst(); 
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.PRIORITY_ADJUSTMENT); 
        assertThat(rec.description()).contains("disabled");
    }

    @Test
    @DisplayName("no recommendation when compliance is met")
    void noRecommendationWhenComplianceMet() { 
        List<PolicyService.Policy> currentPolicies = List.of( 
                new PolicyService.Policy( 
                        "p1", "Data Retention", "desc", "tenant-1",
                        PolicyService.PolicyType.DATA_RETENTION,
                        List.of(), true, 1, Instant.now(), Instant.now() 
                ),
                new PolicyService.Policy( 
                        "p2", "Access Control", "desc", "tenant-1",
                        PolicyService.PolicyType.ACCESS_CONTROL,
                        List.of(), true, 2, Instant.now(), Instant.now() 
                )
        );

        PolicyRecommendationService.ComplianceRequirements requirements =
                new PolicyRecommendationService.ComplianceRequirements( 
                        Set.of("DATA_RETENTION", "ACCESS_CONTROL"), 
                        Map.of(), 
                        Set.of() 
                );

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateComplianceRecommendations(currentPolicies, requirements); 

        assertThat(recommendations).isEmpty(); 
    }

    // ─── Recommendation ordering ─────────────────────────────────────────────────

    @Test
    @DisplayName("recommendations are sorted by priority descending")
    void recommendationsAreSortedByPriority() { 
        List<PolicyViolation> violations = new ArrayList<>(); 
        // Generate 15 violations for HIGH priority
        for (int i = 0; i < 15; i++) { 
            violations.add(new PolicyViolation( 
                    "v" + i, "policy-1", "tenant-1", "user-1",
                    "READ", "resource-1", "TYPE_A",
                    Instant.now(), Map.of() 
            ));
        }
        // Generate 6 user violations for MEDIUM priority
        for (int i = 0; i < 6; i++) { 
            violations.add(new PolicyViolation( 
                    "v" + (15 + i), "policy-1", "tenant-1", "user-2", 
                    "WRITE", "resource-1", "TYPE_B",
                    Instant.now(), Map.of() 
            ));
        }

        List<PolicyRecommendationService.Recommendation> recommendations =
                service.generateRecommendations(violations); 

        assertThat(recommendations).hasSize(2); 
        assertThat(recommendations.get(0).priority()) 
                .isGreaterThanOrEqualTo(recommendations.get(1).priority()); 
    }

    // ─── Recommendation record validation ───────────────────────────────────────

    @Test
    @DisplayName("recommendation record has required fields")
    void recommendationHasRequiredFields() { 
        PolicyRecommendationService.Recommendation rec = new PolicyRecommendationService.Recommendation( 
                "id-1",
                PolicyRecommendationService.RecommendationType.POLICY_CREATION,
                "Test Title",
                "Test Description",
                PolicyRecommendationService.RecommendationPriority.HIGH,
                "policy-1",
                Map.of("key", "value"), 
                Instant.now() 
        );

        assertThat(rec.id()).isEqualTo("id-1");
        assertThat(rec.type()).isEqualTo(PolicyRecommendationService.RecommendationType.POLICY_CREATION); 
        assertThat(rec.title()).isEqualTo("Test Title");
        assertThat(rec.description()).isEqualTo("Test Description");
        assertThat(rec.priority()).isEqualTo(PolicyRecommendationService.RecommendationPriority.HIGH); 
        assertThat(rec.suggestedPolicyId()).isEqualTo("policy-1");
        assertThat(rec.parameters()).containsEntry("key", "value"); 
    }

    @Test
    @DisplayName("priority levels are correctly ordered")
    void priorityLevelsAreOrdered() { 
        assertThat(PolicyRecommendationService.RecommendationPriority.CRITICAL.level()) 
                .isGreaterThan(PolicyRecommendationService.RecommendationPriority.HIGH.level()); 
        assertThat(PolicyRecommendationService.RecommendationPriority.HIGH.level()) 
                .isGreaterThan(PolicyRecommendationService.RecommendationPriority.MEDIUM.level()); 
        assertThat(PolicyRecommendationService.RecommendationPriority.MEDIUM.level()) 
                .isGreaterThan(PolicyRecommendationService.RecommendationPriority.LOW.level()); 
    }
}
