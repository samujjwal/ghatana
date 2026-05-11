/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.api.model;

import java.util.List;
import java.util.Optional;

/**
 * Information about a dependency conflict with actionable resolution suggestions.
 *
 * @doc.type record
 * @doc.purpose Conflict information model with actionable resolution
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ConflictInfo(
        String dependencyName,
        String version1,
        String source1,
        String version2,
        String source2,
        ConflictType type,
        ResolutionSuggestion resolution
) {
    /**
     * Type of conflict.
     */
    public enum ConflictType {
        VERSION_MISMATCH,
        INCOMPATIBLE,
        DUPLICATE,
        CIRCULAR,
        TRANSITIVE,
        EXCLUSION
    }

    /**
     * Actionable resolution suggestion for the conflict.
     */
    public record ResolutionSuggestion(
        ResolutionStrategy strategy,
        String recommendedVersion,
        List<ResolutionStep> steps,
        String rationale,
        boolean requiresManualReview
    ) {
        public static ResolutionSuggestion automatic(String version, String rationale) {
            return new ResolutionSuggestion(
                ResolutionStrategy.AUTO_SELECT_VERSION,
                version,
                List.of(new ResolutionStep(ResolutionStep.StepType.UPDATE_DEPENDENCY, version, "Update to recommended version")),
                rationale,
                false
            );
        }

        public static ResolutionSuggestion manual(String rationale) {
            return new ResolutionSuggestion(
                ResolutionStrategy.MANUAL_REVIEW,
                null,
                List.of(
                    new ResolutionStep(ResolutionStep.StepType.REVIEW_DEPENDENCIES, null, "Review conflicting dependencies"),
                    new ResolutionStep(ResolutionStep.StepType.CONSULT_DOCUMENTATION, null, "Check dependency documentation for compatibility")
                ),
                rationale,
                true
            );
        }

        public static ResolutionSuggestion exclude(String dependency, String rationale) {
            return new ResolutionSuggestion(
                ResolutionStrategy.EXCLUDE_TRANSITIVE,
                null,
                List.of(new ResolutionStep(ResolutionStep.StepType.EXCLUDE_DEPENDENCY, dependency, "Exclude transitive dependency")),
                rationale,
                true
            );
        }
    }

    /**
     * Resolution strategy to apply.
     */
    public enum ResolutionStrategy {
        AUTO_SELECT_VERSION,
        MANUAL_REVIEW,
        EXCLUDE_TRANSITIVE,
        FORCE_VERSION,
        USE_RANGE,
        DOWNGRADE
    }

    /**
     * Individual resolution step.
     */
    public record ResolutionStep(
        StepType type,
        String target,
        String description
    ) {
        public enum StepType {
            UPDATE_DEPENDENCY,
            DOWNGRADE_DEPENDENCY,
            EXCLUDE_DEPENDENCY,
            REVIEW_DEPENDENCIES,
            CONSULT_DOCUMENTATION,
            UPDATE_BUILD_CONFIG,
            RUN_DEPENDENCY_CHECK
        }
    }

    public static ConflictInfo versionMismatch(String name, String v1, String s1, String v2, String s2) {
        String recommendedVersion = compareVersions(v1, v2) > 0 ? v1 : v2;
        String rationale = String.format("Version %s is higher than %s. Using the higher version resolves the mismatch.", recommendedVersion, recommendedVersion.equals(v1) ? v2 : v1);
        
        return new ConflictInfo(
                name, v1, s1, v2, s2,
                ConflictType.VERSION_MISMATCH,
                ResolutionSuggestion.automatic(recommendedVersion, rationale)
        );
    }

    public static ConflictInfo incompatible(String name, String v1, String s1, String v2, String s2) {
        String rationale = String.format("Dependencies %s and %s may be incompatible. Manual review of API compatibility is required.", v1, v2);
        
        return new ConflictInfo(
                name, v1, s1, v2, s2,
                ConflictType.INCOMPATIBLE,
                ResolutionSuggestion.manual(rationale)
        );
    }

    public static ConflictInfo duplicate(String name, String v1, String s1, String v2, String s2) {
        String rationale = String.format("Dependency %s appears in both %s and %s. Remove duplicate declaration.", name, s1, s2);
        
        return new ConflictInfo(
                name, v1, s1, v2, s2,
                ConflictType.DUPLICATE,
                ResolutionSuggestion.manual(rationale)
        );
    }

    public static ConflictInfo transitiveConflict(String name, String requestedVersion, String transitiveVersion, String source) {
        String rationale = String.format("Requested version %s conflicts with transitive version %s from %s. Consider using a version range or excluding the transitive dependency.", requestedVersion, transitiveVersion, source);
        
        return new ConflictInfo(
                name, requestedVersion, "direct", transitiveVersion, source,
                ConflictType.TRANSITIVE,
                ResolutionSuggestion.exclude(name, rationale)
        );
    }

    public static ConflictInfo circular(String name, String source1, String source2) {
        String rationale = String.format("Circular dependency detected involving %s. Break the cycle by removing or refactoring one of the dependencies.", name);
        
        return new ConflictInfo(
                name, "unknown", source1, "unknown", source2,
                ConflictType.CIRCULAR,
                ResolutionSuggestion.manual(rationale)
        );
    }

    /**
     * Get the recommended version if available.
     */
    public Optional<String> getRecommendedVersion() {
        return Optional.ofNullable(resolution.recommendedVersion());
    }

    /**
     * Check if the conflict can be automatically resolved.
     */
    public boolean isAutoResolvable() {
        return !resolution.requiresManualReview();
    }

    /**
     * Get resolution steps as a formatted string.
     */
    public String getResolutionStepsSummary() {
        return resolution.steps().stream()
            .map(step -> "- " + step.type() + ": " + step.description())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("No resolution steps available");
    }

    private static int compareVersions(String v1, String v2) {
        // Simple version comparison
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
