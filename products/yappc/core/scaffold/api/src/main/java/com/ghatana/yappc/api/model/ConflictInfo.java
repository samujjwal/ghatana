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
 * Information about a dependency conflict.
 *
 * @doc.type record
 * @doc.purpose Conflict information model
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
        String resolution
) {
    /**
     * Type of conflict.
     */
    public enum ConflictType {
        VERSION_MISMATCH,
        INCOMPATIBLE,
        DUPLICATE,
        CIRCULAR
    }

    public static ConflictInfo versionMismatch(String name, String v1, String s1, String v2, String s2) {
        return new ConflictInfo(
                name, v1, s1, v2, s2,
                ConflictType.VERSION_MISMATCH,
                "Use the higher version: " + (compareVersions(v1, v2) > 0 ? v1 : v2)
        );
    }

    public static ConflictInfo incompatible(String name, String v1, String s1, String v2, String s2) {
        return new ConflictInfo(
                name, v1, s1, v2, s2,
                ConflictType.INCOMPATIBLE,
                "Manual resolution required"
        );
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
