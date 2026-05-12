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
     * Detects obsolescence due to version mismatches.
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
                    Map.of()
            ));
        }
        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to API deprecations.
     */
    private java.util.Optional<ObsolescenceEvent> detectApiDeprecation(@NotNull MasteryItem item) {
        String deprecatedFlag = item.labels().get("deprecated");
        if ("true".equalsIgnoreCase(deprecatedFlag)) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.API_CHANGE,
                    "API is deprecated",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.of("deprecation", "true")),
                    Map.of()
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
     * Detects obsolescence due to repeated failures.
     */
    private java.util.Optional<ObsolescenceEvent> detectRepeatedFailures(@NotNull MasteryItem item) {
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
                            Map.of()
                    ));
                }
            } catch (NumberFormatException e) {
                // Invalid failure count, ignore
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Detects obsolescence due to security vulnerabilities.
     */
    private java.util.Optional<ObsolescenceEvent> detectSecurityVulnerabilities(@NotNull MasteryItem item) {
        String securityFlag = item.labels().get("securityVulnerability");
        if ("true".equalsIgnoreCase(securityFlag)) {
            return java.util.Optional.of(new ObsolescenceEvent(
                    java.util.UUID.randomUUID().toString(),
                    item.masteryId(),
                    ObsolescenceReason.SECURITY_VULNERABILITY,
                    "Security vulnerability detected",
                    Instant.now(),
                    List.of(ObsolescenceEvidenceRef.securityReport(item.masteryId())),
                    Map.of()
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
