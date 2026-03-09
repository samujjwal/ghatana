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
 * Enumeration of target platforms for scaffold generation.
 *
 * @doc.type enum
 * @doc.purpose Enumerate target platforms (web, desktop, mobile, server) for scaffold packs
 * @doc.layer platform
 * @doc.pattern Catalog
 */
public enum PlatformType {

    WEB("web", "Web Application", List.of("browser"), false),
    SERVER("server", "Server Application", List.of("linux", "windows", "macos"), false),
    DESKTOP("desktop", "Desktop Application", List.of("linux", "windows", "macos"), true),
    MOBILE_IOS("ios", "iOS Mobile", List.of("ios"), true),
    MOBILE_ANDROID("android", "Android Mobile", List.of("android"), true),
    MOBILE_CROSS("mobile", "Cross-platform Mobile", List.of("ios", "android"), true),
    CLI("cli", "Command Line Interface", List.of("linux", "windows", "macos"), false),
    LIBRARY("library", "Reusable Library", List.of(), false),
    EMBEDDED("embedded", "Embedded System", List.of(), true);

    private final String identifier;
    private final String displayName;
    private final List<String> targetOses;
    private final boolean requiresNativeCode;

    PlatformType(String identifier, String displayName, List<String> targetOses,
            boolean requiresNativeCode) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.targetOses = targetOses;
        this.requiresNativeCode = requiresNativeCode;
    }

    /**
     * @return The string identifier for this platform
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
     * @return Target operating systems for this platform
     */
    public List<String> getTargetOses() {
        return targetOses;
    }

    /**
     * @return true if this platform typically requires native/platform-specific code
     */
    public boolean requiresNativeCode() {
        return requiresNativeCode;
    }

    /**
     * Check if this platform targets a specific OS.
     *
     * @param os The operating system identifier
     * @return true if this platform targets the given OS
     */
    public boolean targetsOs(String os) {
        return targetOses.isEmpty() || targetOses.contains(os.toLowerCase());
    }

    /**
     * Find platform type by identifier.
     *
     * @param identifier The platform identifier
     * @return The matching PlatformType or null if not found
     */
    public static PlatformType fromIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        for (PlatformType type : values()) {
            if (type.identifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this is a mobile platform.
     *
     * @return true if this is iOS, Android, or cross-platform mobile
     */
    public boolean isMobile() {
        return this == MOBILE_IOS || this == MOBILE_ANDROID || this == MOBILE_CROSS;
    }

    /**
     * Check if this is a native platform (desktop or mobile).
     *
     * @return true if this targets native platforms
     */
    public boolean isNative() {
        return this == DESKTOP || isMobile() || this == EMBEDDED;
    }
}
