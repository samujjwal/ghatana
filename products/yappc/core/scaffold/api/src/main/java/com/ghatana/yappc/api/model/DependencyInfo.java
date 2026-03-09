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

/**
 * Information about a dependency.
 *
 * @doc.type record
 * @doc.purpose Dependency information model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record DependencyInfo(
        String groupId,
        String artifactId,
        String version,
        String scope,
        DependencyType type,
        boolean transitive
) {
    /**
     * Type of dependency based on the build system.
     */
    public enum DependencyType {
        MAVEN,
        GRADLE,
        NPM,
        CARGO,
        GO_MOD
    }

    public static DependencyInfo maven(String groupId, String artifactId, String version) {
        return new DependencyInfo(groupId, artifactId, version, "compile", DependencyType.MAVEN, false);
    }

    public static DependencyInfo gradle(String groupId, String artifactId, String version) {
        return new DependencyInfo(groupId, artifactId, version, "implementation", DependencyType.GRADLE, false);
    }

    public static DependencyInfo npm(String packageName, String version) {
        return new DependencyInfo(null, packageName, version, "dependencies", DependencyType.NPM, false);
    }

    public static DependencyInfo cargo(String crateName, String version) {
        return new DependencyInfo(null, crateName, version, "dependencies", DependencyType.CARGO, false);
    }

    public static DependencyInfo goMod(String modulePath, String version) {
        return new DependencyInfo(null, modulePath, version, "require", DependencyType.GO_MOD, false);
    }

    public String getCoordinates() {
        if (groupId != null) {
            return groupId + ":" + artifactId + ":" + version;
        }
        return artifactId + "@" + version;
    }
}
