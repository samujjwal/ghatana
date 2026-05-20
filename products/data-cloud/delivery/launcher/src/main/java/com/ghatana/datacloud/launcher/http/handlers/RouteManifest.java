/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Canonical route manifest used to generate:
 * - OpenAPI specification validation
 * - RuntimeTruthPosture.generated.ts for UI feature gating
 * - SDK metadata for cross-service integration
 * - API documentation and route matrices
 *
 * <p>DC-P0-03: Single source of truth for all route metadata across backends.
 *
 * @doc.type class
 * @doc.purpose Canonical route manifest for OpenAPI/runtime/UI consistency
 * @doc.layer product
 * @doc.pattern Configuration Model
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RouteManifest {

    @JsonProperty("version")
    private String version;

    @JsonProperty("lastUpdated")
    private String lastUpdated;

    @JsonProperty("generatedFrom")
    private String generatedFrom;  // Reference to source (e.g., "RouteSecurityRegistry.java")

    @JsonProperty("routes")
    private List<RouteEntry> routes = new ArrayList<>();

    public RouteManifest() {
    }

    public RouteManifest(String version, String lastUpdated, String generatedFrom) {
        this.version = version;
        this.lastUpdated = lastUpdated;
        this.generatedFrom = generatedFrom;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getGeneratedFrom() {
        return generatedFrom;
    }

    public void setGeneratedFrom(String generatedFrom) {
        this.generatedFrom = generatedFrom;
    }

    public List<RouteEntry> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteEntry> routes) {
        this.routes = routes;
    }

    public void addRoute(RouteEntry route) {
        this.routes.add(route);
    }

    /**
     * Individual route entry in the manifest.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class RouteEntry {

        @JsonProperty("method")
        private String method;  // GET, POST, PUT, DELETE, PATCH

        @JsonProperty("path")
        private String path;  // Canonical path (e.g., /api/v1/action/pipelines/{pipelineId})

        @JsonProperty("sensitivity")
        private String sensitivity;  // PUBLIC, INTERNAL, SENSITIVE, CRITICAL

        @JsonProperty("requiresAuth")
        private boolean requiresAuth;

        @JsonProperty("requiresTenant")
        private boolean requiresTenant;

        @JsonProperty("requiresPolicy")
        private boolean requiresPolicy;

        @JsonProperty("idempotent")
        private boolean idempotent;

        @JsonProperty("description")
        private String description;

        @JsonProperty("operationId")
        private String operationId;  // For OpenAPI

        @JsonProperty("tags")
        private List<String> tags = new ArrayList<>();

        @JsonProperty("requestSchema")
        private String requestSchema;  // Reference to OpenAPI schema

        @JsonProperty("responseSchema")
        private String responseSchema;  // Reference to OpenAPI schema

        @JsonProperty("deprecated")
        private Boolean deprecated;

        @JsonProperty("deprecationReason")
        private String deprecationReason;

        @JsonProperty("replacesWith")
        private String replacesWith;  // Pointer to replacement route

        @JsonProperty("runtimeTruthSurface")
        private String runtimeTruthSurface;  // How to expose in UI (VISIBLE, HIDDEN, DEVELOPER_ONLY)

        @JsonProperty("category")
        private String category;  // e.g., "Action Plane", "Event Store", "Context Plane"

        @JsonProperty("metadata")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public RouteEntry() {
        }

        public RouteEntry(String method, String path, String sensitivity) {
            this.method = method;
            this.path = path;
            this.sensitivity = sensitivity;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(String sensitivity) {
            this.sensitivity = sensitivity;
        }

        public boolean isRequiresAuth() {
            return requiresAuth;
        }

        public void setRequiresAuth(boolean requiresAuth) {
            this.requiresAuth = requiresAuth;
        }

        public boolean isRequiresTenant() {
            return requiresTenant;
        }

        public void setRequiresTenant(boolean requiresTenant) {
            this.requiresTenant = requiresTenant;
        }

        public boolean isRequiresPolicy() {
            return requiresPolicy;
        }

        public void setRequiresPolicy(boolean requiresPolicy) {
            this.requiresPolicy = requiresPolicy;
        }

        public boolean isIdempotent() {
            return idempotent;
        }

        public void setIdempotent(boolean idempotent) {
            this.idempotent = idempotent;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getRequestSchema() {
            return requestSchema;
        }

        public void setRequestSchema(String requestSchema) {
            this.requestSchema = requestSchema;
        }

        public String getResponseSchema() {
            return responseSchema;
        }

        public void setResponseSchema(String responseSchema) {
            this.responseSchema = responseSchema;
        }

        public Boolean getDeprecated() {
            return deprecated;
        }

        public void setDeprecated(Boolean deprecated) {
            this.deprecated = deprecated;
        }

        public String getDeprecationReason() {
            return deprecationReason;
        }

        public void setDeprecationReason(String deprecationReason) {
            this.deprecationReason = deprecationReason;
        }

        public String getReplacesWith() {
            return replacesWith;
        }

        public void setReplacesWith(String replacesWith) {
            this.replacesWith = replacesWith;
        }

        public String getRuntimeTruthSurface() {
            return runtimeTruthSurface;
        }

        public void setRuntimeTruthSurface(String runtimeTruthSurface) {
            this.runtimeTruthSurface = runtimeTruthSurface;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}
