/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps obsolescence signals to transition-policy-compatible evidence keys.
 *
 * @doc.type class
 * @doc.purpose Map obsolescence reasons to transition-policy evidence
 * @doc.layer agent-core
 * @doc.pattern Mapper
 */
public final class ObsolescenceEvidenceMapper {

    private ObsolescenceEvidenceMapper() {
        // Utility class
    }

    @NotNull
    public static Map<String, String> toTransitionEvidence(@NotNull ObsolescenceEvent event) {
        Map<String, String> evidence = new HashMap<>();

        switch (event.reason()) {
            case VERSION_MISMATCH -> {
                String newVersion = event.metadata().get("new_active_version_id");
                if (newVersion != null && !newVersion.isBlank()) {
                    evidence.put("new_active_version_id", newVersion);
                    evidence.put("replaced_by_newer", "true");
                } else {
                    evidence.put("end_of_life", "true");
                }
            }
            case API_CHANGE -> evidence.put("api_break", "true");
            case RUNTIME_INCOMPATIBILITY -> evidence.put("api_break", "true");
            case REPEATED_FAILURES, EVAL_REGRESSION -> evidence.put("repeated_failures", "true");
            case SECURITY_VULNERABILITY -> {
                evidence.put("security_advisory", "true");
                evidence.put("safety_violation", "true");
            }
            case DOCUMENTATION_CONTRADICTION, DEPRECATED_PATTERN, DEPRECATED_DEPENDENCY -> evidence.put("contradiction", "true");
            case SUPERSEDED_BY_ALTERNATIVE -> {
                evidence.put("replaced_by_newer", "true");
                evidence.put("no_active_use_case", "true");
            }
        }

        // Preserve policy-critical keys if explicitly supplied by detector metadata.
        copyIfPresent(event.metadata(), evidence, "no_active_use_case");
        copyIfPresent(event.metadata(), evidence, "api_break");
        copyIfPresent(event.metadata(), evidence, "security_advisory");
        copyIfPresent(event.metadata(), evidence, "repeated_failures");
        copyIfPresent(event.metadata(), evidence, "contradiction");
        copyIfPresent(event.metadata(), evidence, "safety_violation");
        copyIfPresent(event.metadata(), evidence, "end_of_life");
        copyIfPresent(event.metadata(), evidence, "new_active_version_id");
        copyIfPresent(event.metadata(), evidence, "replaced_by_newer");

        return Map.copyOf(evidence);
    }

    private static void copyIfPresent(
            @NotNull Map<String, String> source,
            @NotNull Map<String, String> target,
            @NotNull String key) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
