/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.VersionScope;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default implementation of ObsolescenceDetector with support for all detection types.
 *
 * <p>Detects obsolescence based on:
 * <ul>
 *   <li>API changes (signature, behavior, deprecation)</li>
 *   <li>Version mismatches (library/framework versions)</li>
 *   <li>Runtime incompatibility</li>
 *   <li>Repeated failures in execution</li>
 *   <li>Security vulnerabilities</li>
 *   <li>Documentation contradictions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default obsolescence detector implementation
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class DefaultObsolescenceDetector implements ObsolescenceDetector {

    private final MasteryRegistry masteryRegistry;

    /**
     * Creates a default obsolescence detector.
     *
     * @param masteryRegistry mastery registry for scanning items
     */
    public DefaultObsolescenceDetector(@NotNull MasteryRegistry masteryRegistry) {
        this.masteryRegistry = Objects.requireNonNull(masteryRegistry, "masteryRegistry must not be null");
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> detect(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env) {
        List<ObsolescenceEvent> events = new ArrayList<>();

        // Detect version mismatches
        detectVersionObsolescence(item, env).ifPresent(events::add);

        // Detect API deprecations
        detectApiDeprecation(item).ifPresent(events::add);

        // Detect runtime incompatibility
        detectRuntimeIncompatibility(item, env).ifPresent(events::add);

        // Detect repeated failures
        detectRepeatedFailures(item).ifPresent(events::add);

        // Detect security vulnerabilities
        detectSecurityVulnerabilities(item).ifPresent(events::add);

        // Detect documentation contradictions
        detectDocumentationContradictions(item).ifPresent(events::add);

        return Promise.of(events);
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> scanAll(@NotNull EnvironmentFingerprint env) {
        return masteryRegistry.query(com.ghatana.agent.mastery.MasteryQuery.activeOnly())
                .then(items -> {
                    // Chain detection for each item and collect results
                    Promise<List<ObsolescenceEvent>> result = Promise.of(new ArrayList<>());
                    for (MasteryItem item : items) {
                        result = result.then(allEvents -> 
                            detect(item, env).map(itemEvents -> {
                                allEvents.addAll(itemEvents);
                                return allEvents;
                            })
                        );
                    }
                    return result;
                });
    }

    /**
     * Detects obsolescence due to version mismatches and dependency changes.
     * Uses real dependency version information from environment fingerprint.
     */
    private java.util.Optional<ObsolescenceEvent> detectVersionObsolescence(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env) {
        // Check if version scope is stale based on staleness timestamp
        if (item.staleAfter().isBefore(Instant.now())) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.VERSION_MISMATCH,
                    "Mastery item is stale (staleAfter: " + item.staleAfter() + ")",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.versionScope(item.versionScope().toString())),
                    Map.of("staleAfter", item.staleAfter().toString())
            ));
        }

        // Check for dependency changes using real evidence from environment fingerprint
        java.util.Optional<ObsolescenceEvent> dependencyChange = detectDependencyChanges(item, env);
        if (dependencyChange.isPresent()) {
            return dependencyChange;
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to dependency changes.
     * Compares mastery item's expected dependencies with actual environment dependencies.
     */
    private java.util.Optional<ObsolescenceEvent> detectDependencyChanges(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env) {
        // Get expected dependencies from version scope constraints
        Map<String, String> expectedDeps = extractExpectedDependencies(item.versionScope());
        Map<String, String> actualDeps = env.dependencies();

        // Check for missing or changed dependencies
        List<String> changes = new ArrayList<>();
        for (Map.Entry<String, String> expected : expectedDeps.entrySet()) {
            String depName = expected.getKey();
            String expectedRange = expected.getValue();
            String actualVersion = actualDeps.get(depName);

            if (actualVersion == null) {
                changes.add("Missing dependency: " + depName);
            } else if (!isVersionInRange(actualVersion, expectedRange)) {
                changes.add("Version mismatch for " + depName + ": expected " + expectedRange + ", actual " + actualVersion);
            }
        }

        if (!changes.isEmpty()) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.VERSION_MISMATCH,
                    "Dependency changes detected: " + String.join(", ", changes),
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.dependencyChanges(changes)),
                    Map.of("changes", String.join("; ", changes))
            ));
        }

        return java.util.Optional.empty();
    }

    /**
     * Extracts expected dependencies from version scope constraints.
     */
    private Map<String, String> extractExpectedDependencies(@NotNull VersionScope versionScope) {
        Map<String, String> dependencies = new java.util.HashMap<>();
        for (com.ghatana.agent.mastery.VersionConstraint constraint : versionScope.active()) {
            dependencies.put(constraint.name(), constraint.range());
        }
        return dependencies;
    }

    /**
     * Checks if a version falls within a version range.
     * Simplified check for dependency version compatibility.
     */
    private boolean isVersionInRange(@NotNull String version, @NotNull String range) {
        // Handle exact match
        if (!range.startsWith("^") && !range.startsWith("~") && !range.startsWith(">=") && !range.startsWith("<=")) {
            return version.equals(range);
        }

        // Handle caret range (^1.2.3 means >=1.2.3 <2.0.0)
        if (range.startsWith("^")) {
            String base = range.substring(1);
            String[] baseParts = base.split("\\.");
            String[] versionParts = version.split("\\.");
            if (baseParts.length > 0 && versionParts.length > 0) {
                return versionParts[0].equals(baseParts[0]);
            }
        }

        // Handle tilde range (~1.2.3 means >=1.2.3 <1.3.0)
        if (range.startsWith("~")) {
            String base = range.substring(1);
            String[] baseParts = base.split("\\.");
            String[] versionParts = version.split("\\.");
            if (baseParts.length >= 2 && versionParts.length >= 2) {
                return versionParts[0].equals(baseParts[0]) && versionParts[1].equals(baseParts[1]);
            }
        }

        // Handle >=
        if (range.startsWith(">=")) {
            String required = range.substring(2);
            return compareVersions(version, required) >= 0;
        }

        return false;
    }

    /**
     * Simple semantic version comparison.
     */
    private int compareVersions(@NotNull String v1, @NotNull String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (p1 < p2) return -1;
            if (p1 > p2) return 1;
        }

        return 0;
    }

    /**
     * Detects obsolescence due to API deprecations and API diffs.
     * Uses evidence from evaluation refs and known failure modes.
     */
    private java.util.Optional<ObsolescenceEvent> detectApiDeprecation(@NotNull MasteryItem item) {
        // Check for explicit deprecation label (legacy support)
        String deprecatedFlag = item.labels().get("deprecated");
        if ("true".equalsIgnoreCase(deprecatedFlag)) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.API_CHANGE,
                    "API is deprecated",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.of("deprecation", "true")),
                    Map.of("deprecated", "true")
            ));
        }

        // Detect API diffs from evaluation refs (real evidence)
        java.util.Optional<ObsolescenceEvent> apiDiff = detectApiDiffs(item);
        if (apiDiff.isPresent()) {
            return apiDiff;
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects API diffs from evaluation refs and known failure modes.
     * Uses real evidence from evaluation results and failure patterns.
     */
    private java.util.Optional<ObsolescenceEvent> detectApiDiffs(@NotNull MasteryItem item) {
        List<String> apiChanges = new ArrayList<>();

        // Check evaluation refs for API signature changes
        for (String evalRef : item.evaluationRefs()) {
            if (evalRef.contains("signature-change") || evalRef.contains("api-break")) {
                apiChanges.add("API signature change detected in evaluation: " + evalRef);
            }
        }

        // Check known failure modes for API-related failures
        for (String failureRef : item.knownFailureModeIds()) {
            if (failureRef.contains("api") || failureRef.contains("signature") || failureRef.contains("deprecation")) {
                apiChanges.add("API-related failure mode: " + failureRef);
            }
        }

        if (!apiChanges.isEmpty()) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.API_CHANGE,
                    "API changes detected: " + String.join(", ", apiChanges),
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.apiDiffs(apiChanges)),
                    Map.of("apiChanges", String.join("; ", apiChanges))
            ));
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to runtime incompatibility.
     */
    private java.util.Optional<ObsolescenceEvent> detectRuntimeIncompatibility(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env) {
        String requiredRuntime = item.labels().get("requiredRuntime");
        if (requiredRuntime != null && !requiredRuntime.isBlank()) {
            // Check if required runtime is in the environment's runtimes map
            String actualRuntime = env.runtimes().get(requiredRuntime);
            if (actualRuntime == null) {
                return java.util.Optional.of(new ObsolescenceEvent(
                        java.util.UUID.randomUUID().toString(),
                        item.masteryId(),
                        ObsolescenceReason.RUNTIME_INCOMPATIBILITY,
                        "Runtime not available: required=" + requiredRuntime,
                        Instant.now(),
                        List.of(ObsolescenceEvidenceRef.of("runtime", requiredRuntime)),
                        Map.of()
                ));
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to repeated failures and test failures.
     * Uses real evidence from known failure modes and evaluation refs.
     */
    private java.util.Optional<ObsolescenceEvent> detectRepeatedFailures(@NotNull MasteryItem item) {
        // Check for explicit failure count label (legacy support)
        String failureCountStr = item.labels().get("failureCount");
        if (failureCountStr != null) {
            try {
                int failureCount = Integer.parseInt(failureCountStr);
                if (failureCount >= 5) { // Threshold for repeated failures
                    return java.util.Optional.of(new ObsolescenceEvent(
                            java.util.UUID.randomUUID().toString(),
                            item.masteryId(),
                            ObsolescenceReason.REPEATED_FAILURES,
                            "Repeated failures detected: " + failureCount + " occurrences",
                            Instant.now(),
                            List.of(ObsolescenceEvidenceRef.of("failure_count", failureCountStr)),
                            Map.of("failureCount", failureCountStr)
                    ));
                }
            } catch (NumberFormatException e) {
                // Invalid failure count, ignore
            }
        }

        // Detect test failures from evaluation refs (real evidence)
        java.util.Optional<ObsolescenceEvent> testFailure = detectTestFailures(item);
        if (testFailure.isPresent()) {
            return testFailure;
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects test failures from evaluation refs and known failure modes.
     * Uses real evidence from test execution results.
     */
    private java.util.Optional<ObsolescenceEvent> detectTestFailures(@NotNull MasteryItem item) {
        List<String> testFailures = new ArrayList<>();

        // Check evaluation refs for test failures
        for (String evalRef : item.evaluationRefs()) {
            if (evalRef.contains("test-failure") || evalRef.contains("test-error") || evalRef.contains("assertion-failed")) {
                testFailures.add("Test failure detected in evaluation: " + evalRef);
            }
        }

        // Check known failure modes for test-related failures
        for (String failureRef : item.knownFailureModeIds()) {
            if (failureRef.contains("test") || failureRef.contains("assertion") || failureRef.contains("validation")) {
                testFailures.add("Test-related failure mode: " + failureRef);
            }
        }

        // If there are multiple test failures, consider the item obsolete
        if (testFailures.size() >= 3) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.REPEATED_FAILURES,
                    "Test failures detected: " + testFailures.size() + " occurrences",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.testFailures(testFailures)),
                    Map.of("testFailureCount", String.valueOf(testFailures.size()))
            ));
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to security vulnerabilities.
     * Uses real evidence from security advisories and known failure modes.
     */
    private java.util.Optional<ObsolescenceEvent> detectSecurityVulnerabilities(@NotNull MasteryItem item) {
        // Check for explicit security flag (legacy support)
        String securityFlag = item.labels().get("securityVulnerability");
        if ("true".equalsIgnoreCase(securityFlag)) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.SECURITY_VULNERABILITY,
                    "Security vulnerability detected",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.securityReport(item.masteryId())),
                    Map.of("securityVulnerability", "true")
            ));
        }

        // Detect security advisories from evidence refs (real evidence)
        java.util.Optional<ObsolescenceEvent> securityAdvisory = detectSecurityAdvisories(item);
        if (securityAdvisory.isPresent()) {
            return securityAdvisory;
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects security advisories from evidence refs and known failure modes.
     * Uses real evidence from security reports and advisories.
     */
    private java.util.Optional<ObsolescenceEvent> detectSecurityAdvisories(@NotNull MasteryItem item) {
        List<String> securityIssues = new ArrayList<>();

        // Check evidence refs for security advisories
        for (String evidenceRef : item.evidenceRefs()) {
            if (evidenceRef.contains("cve") || evidenceRef.contains("security-advisory") || evidenceRef.contains("vulnerability")) {
                securityIssues.add("Security advisory detected: " + evidenceRef);
            }
        }

        // Check known failure modes for security-related failures
        for (String failureRef : item.knownFailureModeIds()) {
            if (failureRef.contains("security") || failureRef.contains("cve") || failureRef.contains("vulnerability")) {
                securityIssues.add("Security-related failure mode: " + failureRef);
            }
        }

        if (!securityIssues.isEmpty()) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.SECURITY_VULNERABILITY,
                    "Security advisories detected: " + String.join(", ", securityIssues),
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.securityAdvisories(securityIssues)),
                    Map.of("securityIssues", String.join("; ", securityIssues))
            ));
        }

        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to documentation contradictions.
     */
    private java.util.Optional<ObsolescenceEvent> detectDocumentationContradictions(@NotNull MasteryItem item) {
        String docContradictionFlag = item.labels().get("documentationContradiction");
        if ("true".equalsIgnoreCase(docContradictionFlag)) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.DOCUMENTATION_CONTRADICTION,
                    "Documentation contradiction detected",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.documentation(item.masteryId())),
                    Map.of()
            ));
        }
        return java.util.Optional.empty();
    }
}
