/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating policy recommendations based on violation analysis,
 * compliance gaps, and access patterns.
 *
 * <p>This service analyzes policy violations, access patterns, and compliance
 * requirements to generate actionable policy recommendations including:
 * <ul>
 *   <li>Policy configuration optimizations</li>
 *   <li>New policy suggestions based on patterns</li>
 *   <li>Compliance gap remediation recommendations</li>
 *   <li>Policy priority adjustments</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Policy recommendation engine for governance optimization
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PolicyRecommendationService {

    private final ViolationAnalyzer violationAnalyzer;
    private final AccessPatternAnalyzer patternAnalyzer;
    private final ComplianceGapAnalyzer complianceAnalyzer;

    /**
     * Creates a policy recommendation service.
     */
    public PolicyRecommendationService() {
        this.violationAnalyzer = new ViolationAnalyzer();
        this.patternAnalyzer = new AccessPatternAnalyzer();
        this.complianceAnalyzer = new ComplianceGapAnalyzer();
    }

    /**
     * Generates recommendations based on policy violations.
     *
     * @param violations list of policy violations
     * @return list of recommendations
     */
    public List<Recommendation> generateRecommendations(List<PolicyViolation> violations) {
        List<Recommendation> recommendations = new ArrayList<>();

        // Analyze violation patterns
        recommendations.addAll(violationAnalyzer.analyze(violations));

        return recommendations.stream()
                .sorted(Comparator.comparing(Recommendation::priority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Generates recommendations based on access patterns.
     *
     * @param accessPatterns list of access patterns
     * @return list of recommendations
     */
    public List<Recommendation> generatePatternRecommendations(List<AccessPattern> accessPatterns) {
        return patternAnalyzer.analyze(accessPatterns);
    }

    /**
     * Generates compliance gap recommendations.
     *
     * @param currentPolicies current policy set
     * @param requirements compliance requirements
     * @return list of recommendations
     */
    public List<Recommendation> generateComplianceRecommendations(
            List<PolicyService.Policy> currentPolicies,
            ComplianceRequirements requirements) {
        return complianceAnalyzer.analyze(currentPolicies, requirements);
    }

    /**
     * Recommendation record.
     */
    public record Recommendation(
        String id,
        RecommendationType type,
        String title,
        String description,
        RecommendationPriority priority,
        String suggestedPolicyId,
        Map<String, Object> parameters,
        Instant generatedAt
    ) {
        public Recommendation {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            Objects.requireNonNull(title);
            Objects.requireNonNull(description);
            Objects.requireNonNull(priority);
        }
    }

    /**
     * Recommendation type.
     */
    public enum RecommendationType {
        POLICY_CREATION,
        POLICY_MODIFICATION,
        POLICY_DELETION,
        PRIORITY_ADJUSTMENT,
        COMPLIANCE_REMEDIATION,
        ACCESS_CONTROL_IMPROVEMENT
    }

    /**
     * Recommendation priority.
     */
    public enum RecommendationPriority {
        CRITICAL(3),
        HIGH(2),
        MEDIUM(1),
        LOW(0);

        private final int level;

        RecommendationPriority(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }

    /**
     * Access pattern for analysis.
     */
    public record AccessPattern(
        String userId,
        String tenantId,
        String resource,
        String action,
        int accessCount,
        Instant lastAccess,
        boolean frequentlyAccessed
    ) {}

    /**
     * Compliance requirements.
     */
    public record ComplianceRequirements(
        Set<String> requiredPolicyTypes,
        Map<String, String> requiredAttributes,
        Set<String> mandatoryApprovals
    ) {}

    // ─── Inner Analyzers ─────────────────────────────────────────────────────

    private static class ViolationAnalyzer {
        List<Recommendation> analyze(List<PolicyViolation> violations) {
            List<Recommendation> recommendations = new ArrayList<>();

            // Group violations by type
            Map<String, Long> violationCounts = violations.stream()
                    .collect(Collectors.groupingBy(
                            PolicyViolation::violationType,
                            Collectors.counting()));

            // Generate recommendations based on violation frequency
            violationCounts.forEach((type, count) -> {
                if (count > 10) {
                    recommendations.add(new Recommendation(
                            UUID.randomUUID().toString(),
                            RecommendationType.POLICY_MODIFICATION,
                            "High violation rate for " + type,
                            String.format("Policy '%s' has generated %d violations. Consider adjusting rules or adding exceptions.", type, count),
                            RecommendationPriority.HIGH,
                            null,
                            Map.of("violationType", type, "count", count),
                            Instant.now()
                    ));
                }
            });

            // Check for repeated violations by user
            Map<String, Long> userViolations = violations.stream()
                    .collect(Collectors.groupingBy(
                            PolicyViolation::userId,
                            Collectors.counting()));

            userViolations.forEach((userId, count) -> {
                if (count > 5 && count <= 10) {
                    recommendations.add(new Recommendation(
                            UUID.randomUUID().toString(),
                            RecommendationType.ACCESS_CONTROL_IMPROVEMENT,
                            "User requires policy review",
                            String.format("User '%s' has %d policy violations. Consider additional training or policy adjustments.", userId, count),
                            RecommendationPriority.MEDIUM,
                            null,
                            Map.of("userId", userId, "violationCount", count),
                            Instant.now()
                    ));
                }
            });

            return recommendations;
        }
    }

    private static class AccessPatternAnalyzer {
        List<Recommendation> analyze(List<AccessPattern> patterns) {
            List<Recommendation> recommendations = new ArrayList<>();

            // Identify frequently accessed resources without policies
            List<AccessPattern> frequentPatterns = patterns.stream()
                    .filter(AccessPattern::frequentlyAccessed)
                    .toList();

            if (!frequentPatterns.isEmpty()) {
                recommendations.add(new Recommendation(
                        UUID.randomUUID().toString(),
                        RecommendationType.POLICY_CREATION,
                        "Frequent access detected",
                        String.format("%d resources are frequently accessed. Consider adding specific access control policies.", frequentPatterns.size()),
                        RecommendationPriority.MEDIUM,
                        null,
                        Map.of("frequentAccessCount", frequentPatterns.size()),
                        Instant.now()
                ));
            }

            // Identify orphaned access patterns (no recent access)
            Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(90));
            List<AccessPattern> orphanedPatterns = patterns.stream()
                    .filter(p -> p.lastAccess().isBefore(cutoff))
                    .toList();

            if (!orphanedPatterns.isEmpty()) {
                recommendations.add(new Recommendation(
                        UUID.randomUUID().toString(),
                        RecommendationType.POLICY_DELETION,
                        "Orphaned access policies detected",
                        String.format("%d access patterns have no activity in 90 days. Consider removing associated policies.", orphanedPatterns.size()),
                        RecommendationPriority.LOW,
                        null,
                        Map.of("orphanedCount", orphanedPatterns.size()),
                        Instant.now()
                ));
            }

            return recommendations;
        }
    }

    private static class ComplianceGapAnalyzer {
        List<Recommendation> analyze(
                List<PolicyService.Policy> currentPolicies,
                ComplianceRequirements requirements) {

            List<Recommendation> recommendations = new ArrayList<>();

            // Check for missing required policy types
            Set<PolicyService.PolicyType> currentTypes = currentPolicies.stream()
                    .map(PolicyService.Policy::type)
                    .collect(Collectors.toSet());

            Set<String> requiredTypes = requirements.requiredPolicyTypes();
            Set<String> missingTypes = requiredTypes.stream()
                    .filter(type -> !currentTypes.stream()
                            .anyMatch(pt -> pt.name().equals(type)))
                    .collect(Collectors.toSet());

            if (!missingTypes.isEmpty()) {
                recommendations.add(new Recommendation(
                        UUID.randomUUID().toString(),
                        RecommendationType.COMPLIANCE_REMEDIATION,
                        "Missing required policy types",
                        String.format("Required policy types missing: %s. Create policies to meet compliance requirements.", missingTypes),
                        RecommendationPriority.CRITICAL,
                        null,
                        Map.of("missingTypes", missingTypes),
                        Instant.now()
                ));
            }

            // Check for disabled policies that should be enabled
            long disabledCount = currentPolicies.stream()
                    .filter(p -> !p.enabled())
                    .count();

            if (disabledCount > 0) {
                recommendations.add(new Recommendation(
                        UUID.randomUUID().toString(),
                        RecommendationType.PRIORITY_ADJUSTMENT,
                        "Disabled policies detected",
                        String.format("%d policies are disabled. Review and enable if needed for compliance.", disabledCount),
                        RecommendationPriority.MEDIUM,
                        null,
                        Map.of("disabledCount", disabledCount),
                        Instant.now()
                ));
            }

            return recommendations;
        }
    }
}
