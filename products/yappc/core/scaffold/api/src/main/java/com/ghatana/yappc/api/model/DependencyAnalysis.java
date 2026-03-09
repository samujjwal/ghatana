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
import java.util.Objects;

/**
 * Result of dependency analysis.
 *
 * @doc.type record
 * @doc.purpose Dependency analysis result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record DependencyAnalysis(
        String targetName,
        AnalysisType type,
        List<DependencyInfo> directDependencies,
        List<DependencyInfo> transitiveDependencies,
        List<ConflictInfo> conflicts,
        int totalCount,
        boolean hasVulnerabilities
) {
    public enum AnalysisType {
        PACK,
        PROJECT
    }

    public DependencyAnalysis {
        Objects.requireNonNull(targetName, "targetName is required");
        Objects.requireNonNull(type, "type is required");
        directDependencies = directDependencies != null ? List.copyOf(directDependencies) : List.of();
        transitiveDependencies = transitiveDependencies != null ? List.copyOf(transitiveDependencies) : List.of();
        conflicts = conflicts != null ? List.copyOf(conflicts) : List.of();
    }

    public static DependencyAnalysis ofPack(String packName, List<DependencyInfo> dependencies) {
        return new DependencyAnalysis(
                packName,
                AnalysisType.PACK,
                dependencies,
                List.of(),
                List.of(),
                dependencies.size(),
                false
        );
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
