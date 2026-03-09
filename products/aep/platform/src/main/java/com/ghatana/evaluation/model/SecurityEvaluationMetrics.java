package com.ghatana.evaluation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Security metrics collected during an evaluation.
 * This class captures metrics related to vulnerabilities, dependencies, and compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityEvaluationMetrics {

    /**
     * The number of critical vulnerabilities detected.
     */
    private Integer criticalVulnerabilityCount;

    /**
     * The number of high vulnerabilities detected.
     */
    private Integer highVulnerabilityCount;

    /**
     * The number of medium vulnerabilities detected.
     */
    private Integer mediumVulnerabilityCount;

    /**
     * The number of low vulnerabilities detected.
     */
    private Integer lowVulnerabilityCount;

    /**
     * The CVSS score (0-10).
     */
    private Double cvssScore;

    /**
     * The number of dependencies with vulnerabilities.
     */
    private Integer vulnerableDependencyCount;

    /**
     * The total number of dependencies.
     */
    private Integer totalDependencyCount;

    /**
     * The number of outdated dependencies.
     */
    private Integer outdatedDependencyCount;

    /**
     * The list of detected vulnerabilities.
     */
    private List<Vulnerability> vulnerabilities;

    /**
     * The list of compliance issues.
     */
    private List<ComplianceIssue> complianceIssues;

    /**
     * Additional security metrics.
     */
    private Map<String, Object> additionalMetrics;

    /**
     * Represents a detected vulnerability.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Vulnerability {
        /**
         * The vulnerability ID (e.g., CVE-2021-44228).
         */
        private String id;

        /**
         * The vulnerability title.
         */
        private String title;

        /**
         * The vulnerability description.
         */
        private String description;

        /**
         * The vulnerability severity (CRITICAL, HIGH, MEDIUM, LOW).
         */
        private String severity;

        /**
         * The CVSS score (0-10).
         */
        private Double cvssScore;

        /**
         * The affected component.
         */
        private String affectedComponent;

        /**
         * The affected version range.
         */
        private String affectedVersionRange;

        /**
         * The fixed version.
         */
        private String fixedVersion;

        /**
         * The reference URLs.
         */
        private List<String> references;
    }

    /**
     * Represents a compliance issue.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComplianceIssue {
        /**
         * The compliance rule ID.
         */
        private String ruleId;

        /**
         * The compliance rule name.
         */
        private String ruleName;

        /**
         * The compliance rule description.
         */
        private String description;

        /**
         * The compliance issue severity (CRITICAL, HIGH, MEDIUM, LOW).
         */
        private String severity;

        /**
         * The affected component.
         */
        private String affectedComponent;

        /**
         * The remediation steps.
         */
        private String remediation;
    }
}
