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

package com.ghatana.yappc.core.model;

import java.util.List;

/**
 * Enumeration of supported programming languages for scaffold generation.
 *
 * @doc.type enum
 * @doc.purpose Enumerate programming languages supported by scaffold packs
 * @doc.layer platform
 * @doc.pattern Catalog
 */
public enum LanguageType {

    JAVA("java", "Java", List.of(".java"), List.of("gradle", "gradle-kts", "maven")),
    KOTLIN("kotlin", "Kotlin", List.of(".kt", ".kts"), List.of("gradle", "gradle-kts", "maven")),
    TYPESCRIPT("typescript", "TypeScript", List.of(".ts", ".tsx"), List.of("pnpm", "npm", "yarn")),
    JAVASCRIPT("javascript", "JavaScript", List.of(".js", ".jsx"), List.of("pnpm", "npm", "yarn")),
    RUST("rust", "Rust", List.of(".rs"), List.of("cargo")),
    GO("go", "Go", List.of(".go"), List.of("go")),
    CPP("cpp", "C++", List.of(".cpp", ".hpp", ".h"), List.of("cmake", "make")),
    C("c", "C", List.of(".c", ".h"), List.of("cmake", "make")),
    PYTHON("python", "Python", List.of(".py"), List.of("pip", "poetry")),
    SWIFT("swift", "Swift", List.of(".swift"), List.of("spm", "xcode")),
    OBJECTIVE_C("objc", "Objective-C", List.of(".m", ".h"), List.of("xcode"));

    private final String identifier;
    private final String displayName;
    private final List<String> fileExtensions;
    private final List<String> compatibleBuildSystems;

    LanguageType(String identifier, String displayName, List<String> fileExtensions,
            List<String> compatibleBuildSystems) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.fileExtensions = fileExtensions;
        this.compatibleBuildSystems = compatibleBuildSystems;
    }

    /**
     * @return The string identifier for this language
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * @return The human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return The typical file extensions for this language
     */
    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * @return List of compatible build system identifiers
     */
    public List<String> getCompatibleBuildSystems() {
        return compatibleBuildSystems;
    }

    /**
     * Check if this language is compatible with the given build system.
     *
     * @param buildSystemId The build system identifier
     * @return true if compatible
     */
    public boolean isCompatibleWith(String buildSystemId) {
        return compatibleBuildSystems.contains(buildSystemId.toLowerCase());
    }

    /**
     * Find language type by identifier.
     *
     * @param identifier The language identifier
     * @return The matching LanguageType or null if not found
     */
    public static LanguageType fromIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        for (LanguageType type : values()) {
            if (type.identifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Detect language type from file extension.
     *
     * @param fileName The file name or extension
     * @return The matching LanguageType or null if not detected
     */
    public static LanguageType fromFileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lowerName = fileName.toLowerCase();
        for (LanguageType type : values()) {
            for (String ext : type.fileExtensions) {
                if (lowerName.endsWith(ext)) {
                    return type;
                }
            }
        }
        return null;
    }
}
