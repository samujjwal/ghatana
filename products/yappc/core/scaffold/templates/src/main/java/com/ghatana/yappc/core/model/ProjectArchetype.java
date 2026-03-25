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

/**
 * Enumeration of project archetypes for scaffold generation.
 *
 * @doc.type enum
 * @doc.purpose Enumerate project archetypes (service, library, UI, middleware) for scaffold packs
 * @doc.layer platform
 * @doc.pattern Catalog
 */
public enum ProjectArchetype {

    // Backend Services
    SERVICE("service", "Backend Service", "HTTP/gRPC service with business logic"),
    MICROSERVICE("microservice", "Microservice", "Lightweight, focused microservice"),
    API_GATEWAY("api-gateway", "API Gateway", "API gateway/proxy service"),
    WORKER("worker", "Background Worker", "Background job processor"),

    // Frontend Applications
    WEB_APP("webapp", "Web Application", "Single-page or multi-page web application"),
    MOBILE_APP("mobile", "Mobile Application", "Native or cross-platform mobile app"),
    DESKTOP_APP("desktop", "Desktop Application", "Native desktop application"),
    PWA("pwa", "Progressive Web App", "Installable progressive web application"),

    // Libraries and SDKs
    LIBRARY("library", "Library", "Reusable code library"),
    SDK("sdk", "SDK", "Software development kit"),
    PLUGIN("plugin", "Plugin", "Extension/plugin for another system"),

    // Infrastructure
    MIDDLEWARE("middleware", "Middleware", "Service mesh, proxy, or integration layer"),
    EVENT_PROCESSOR("event-processor", "Event Processor", "Event-driven processing service"),
    DATA_PIPELINE("data-pipeline", "Data Pipeline", "Data processing/ETL pipeline"),

    // Full-Stack Compositions
    FULLSTACK_MONOREPO("fullstack-monorepo", "Full-Stack Monorepo", "Combined frontend + backend in monorepo"),
    FULLSTACK_MULTI("fullstack-multi", "Full-Stack Multi-Repo", "Coordinated frontend + backend repos"),

    // Tools
    CLI_TOOL("cli", "CLI Tool", "Command-line interface application"),
    DEVTOOL("devtool", "Development Tool", "Developer tooling and utilities");

    private final String identifier;
    private final String displayName;
    private final String description;

    ProjectArchetype(String identifier, String displayName, String description) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * @return The string identifier for this archetype
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
     * @return Description of this archetype
     */
    public String getDescription() {
        return description;
    }

    /**
     * Find archetype by identifier.
     *
     * @param identifier The archetype identifier
     * @return The matching ProjectArchetype or null if not found
     */
    public static ProjectArchetype fromIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        for (ProjectArchetype type : values()) {
            if (type.identifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this archetype is a frontend type.
     *
     * @return true if this is a web, mobile, or desktop app
     */
    public boolean isFrontend() {
        return this == WEB_APP || this == MOBILE_APP || this == DESKTOP_APP || this == PWA;
    }

    /**
     * Check if this archetype is a backend type.
     *
     * @return true if this is a service, worker, or gateway
     */
    public boolean isBackend() {
        return this == SERVICE || this == MICROSERVICE || this == API_GATEWAY
                || this == WORKER || this == EVENT_PROCESSOR;
    }

    /**
     * Check if this archetype is a full-stack composition.
     *
     * @return true if this combines frontend and backend
     */
    public boolean isFullStack() {
        return this == FULLSTACK_MONOREPO || this == FULLSTACK_MULTI;
    }
}
