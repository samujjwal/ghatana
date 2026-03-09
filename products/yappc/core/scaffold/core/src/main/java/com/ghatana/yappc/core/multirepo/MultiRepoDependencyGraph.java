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

package com.ghatana.yappc.core.multirepo;

import com.ghatana.yappc.core.model.ProjectSpec;
import java.util.Map;
import java.util.Set;

/**
 * Day 21: Multi-repository dependency graph. Represents cross-repository dependencies and their
 * relationships.
 *
 * @doc.type class
 * @doc.purpose Day 21: Multi-repository dependency graph. Represents cross-repository dependencies and their
 * @doc.layer platform
 * @doc.pattern Component
 */
public class MultiRepoDependencyGraph {

    private final Map<String, Set<String>> dependencies;
    private final Map<String, ProjectSpec> projectSpecs;

    public MultiRepoDependencyGraph(
            Map<String, Set<String>> dependencies, Map<String, ProjectSpec> projectSpecs) {
        this.dependencies = dependencies;
        this.projectSpecs = projectSpecs;
    }

    public Map<String, Set<String>> getDependencies() {
        return dependencies;
    }

    public Map<String, ProjectSpec> getProjectSpecs() {
        return projectSpecs;
    }

    /**
 * Gets direct dependencies for a specific project. */
    public Set<String> getDependenciesFor(String projectName) {
        return dependencies.get(projectName);
    }

    /**
 * Gets projects that depend on the specified project. */
    public Set<String> getDependentsOf(String projectName) {
        return dependencies.entrySet().stream()
                .filter(entry -> entry.getValue().contains(projectName))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
 * Checks if there are circular dependencies in the graph. */
    public boolean hasCircularDependencies() {
        // Simple cycle detection using DFS
        Set<String> visited = new java.util.HashSet<>();
        Set<String> recursionStack = new java.util.HashSet<>();

        for (String project : dependencies.keySet()) {
            if (!visited.contains(project)) {
                if (hasCycleUtil(project, visited, recursionStack)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasCycleUtil(String project, Set<String> visited, Set<String> recursionStack) {
        visited.add(project);
        recursionStack.add(project);

        Set<String> deps = dependencies.get(project);
        if (deps != null) {
            for (String dep : deps) {
                if (!visited.contains(dep)) {
                    if (hasCycleUtil(dep, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(dep)) {
                    return true;
                }
            }
        }

        recursionStack.remove(project);
        return false;
    }

    @Override
    public String toString() {
        return String.format(
                "MultiRepoDependencyGraph{projects=%d, dependencies=%d}",
                projectSpecs.size(), dependencies.size());
    }
}
