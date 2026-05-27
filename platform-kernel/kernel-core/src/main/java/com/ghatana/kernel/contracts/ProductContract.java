/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for product-level route and capability declarations.
 *
 * <p>A ProductContract defines the complete surface of a product: its routes,
 * capabilities, role-based access rules, and entitlement metadata. This serves
 * as the canonical source for generating web route manifests, backend entitlement
 * payloads, and access control policies.</p>
 *
 * @doc.type class
 * @doc.purpose Product-level contract for routes, capabilities, and entitlements
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ProductContract extends KernelContract {

    /**
     * Route lifecycle state for no-legacy/no-deprecation mode.
     */
    public enum RouteState {
        /**
         * Route is active and stable.
         */
        ACTIVE,
        /**
         * Route is deprecated and will be removed in a future version.
         */
        DEPRECATED,
        /**
         * Route has been removed and should not be used.
         */
        REMOVED,
        /**
         * Route is in migration and temporarily available.
         */
        MIGRATION
    }

    /**
     * UI state requirements for a route.
     */
    public record UIStateDeclaration(
            boolean requiresLoading,
            boolean requiresError,
            boolean requiresEmpty,
            boolean requiresForbidden,
            String loadingMessageKey,
            String errorMessageKey,
            String emptyMessageKey,
            String forbiddenMessageKey) {
        public UIStateDeclaration {
            if (requiresLoading && loadingMessageKey == null || loadingMessageKey.isBlank()) {
                throw new IllegalArgumentException("loadingMessageKey required when requiresLoading is true");
            }
            if (requiresError && errorMessageKey == null || errorMessageKey.isBlank()) {
                throw new IllegalArgumentException("errorMessageKey required when requiresError is true");
            }
            if (requiresEmpty && emptyMessageKey == null || emptyMessageKey.isBlank()) {
                throw new IllegalArgumentException("emptyMessageKey required when requiresEmpty is true");
            }
            if (requiresForbidden && forbiddenMessageKey == null || forbiddenMessageKey.isBlank()) {
                throw new IllegalArgumentException("forbiddenMessageKey required when requiresForbidden is true");
            }
        }
    }

    /**
     * Accessibility and i18n metadata for a route.
     */
    public record AccessibilityDeclaration(
            String titleKey,
            String descriptionKey,
            String ariaLabelKey,
            boolean keyboardNavigable,
            boolean screenReaderAnnounce) {
        public AccessibilityDeclaration {
            Objects.requireNonNull(titleKey, "titleKey required");
            Objects.requireNonNull(descriptionKey, "descriptionKey required");
            if (titleKey.isBlank()) {
                throw new IllegalArgumentException("titleKey cannot be blank");
            }
            if (descriptionKey.isBlank()) {
                throw new IllegalArgumentException("descriptionKey cannot be blank");
            }
        }
    }

    /**
     * Declares a product route with entitlement metadata.
     */
    public record RouteDeclaration(
            String id,
            String path,
            String method,
            List<String> requiredRoles,
            List<String> requiredCapabilities,
            Map<String, String> metadata,
            RouteState state,
            UIStateDeclaration uiState,
            AccessibilityDeclaration accessibility) {
        public RouteDeclaration {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(path, "path required");
            Objects.requireNonNull(method, "method required");
            Objects.requireNonNull(state, "state required");
            Objects.requireNonNull(uiState, "uiState required");
            Objects.requireNonNull(accessibility, "accessibility required");
            if (requiredRoles == null) requiredRoles = List.of();
            if (requiredCapabilities == null) requiredCapabilities = List.of();
            if (metadata == null) metadata = Map.of();
        }
    }

    /**
     * Declares a product capability with scope and access rules.
     */
    public record CapabilityDeclaration(
            String id,
            String name,
            String description,
            List<String> requiredRoles,
            Map<String, String> metadata) {
        public CapabilityDeclaration {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(name, "name required");
            if (requiredRoles == null) requiredRoles = List.of();
            if (metadata == null) metadata = Map.of();
        }
    }

    /**
     * Declares a persona with its default role and permissions.
     */
    public record PersonaDeclaration(
            String id,
            String name,
            String defaultRole,
            List<String> allowedCapabilities,
            Map<String, String> metadata) {
        public PersonaDeclaration {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(name, "name required");
            Objects.requireNonNull(defaultRole, "defaultRole required");
            if (allowedCapabilities == null) allowedCapabilities = List.of();
            if (metadata == null) metadata = Map.of();
        }
    }

    private final List<RouteDeclaration> routes;
    private final List<CapabilityDeclaration> capabilities;
    private final List<PersonaDeclaration> personas;
    private final String productId;
    private final boolean noLegacyMode;

    private ProductContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              KernelContract.ContractFamily.API, builder.metadata);
        this.routes = builder.routes != null ? List.copyOf(builder.routes) : List.of();
        this.capabilities = builder.capabilities != null ? List.copyOf(builder.capabilities) : List.of();
        this.personas = builder.personas != null ? List.copyOf(builder.personas) : List.of();
        this.productId = builder.productId;
        this.noLegacyMode = builder.noLegacyMode;
        validate();
    }

    public List<RouteDeclaration> getRoutes() { return routes; }
    public List<CapabilityDeclaration> getCapabilities() { return capabilities; }
    public List<PersonaDeclaration> getPersonas() { return personas; }
    public String getProductId() { return productId; }
    public boolean isNoLegacyMode() { return noLegacyMode; }

    @Override
    protected void validate() {
        super.validate();
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId cannot be blank");
        }
        for (RouteDeclaration route : routes) {
            if (!route.path().startsWith("/")) {
                throw new IllegalArgumentException("Route path must start with /: " + route.path());
            }
            String upper = route.method().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
                      .contains(upper)) {
                throw new IllegalArgumentException("Invalid HTTP method: " + route.method());
            }
            // In no-legacy mode, reject deprecated/removed/migration states
            if (noLegacyMode && route.state() != RouteState.ACTIVE) {
                throw new IllegalArgumentException(
                    "No-legacy mode enabled: route " + route.id() + " has state " + route.state() +
                    ", only ACTIVE state is allowed"
                );
            }
        }
        for (CapabilityDeclaration capability : capabilities) {
            if (capability.id().isBlank()) {
                throw new IllegalArgumentException("Capability id cannot be blank");
            }
        }
    }

    /**
     * Creates a new builder for {@link ProductContract}.
     */
    public static Builder builder(String contractId, String name, String version, String productId) {
        return new Builder(contractId, name, version, productId);
    }

    /**
     * Fluent builder for {@link ProductContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private final String productId;
        private Map<String, String> metadata = Map.of();
        private List<RouteDeclaration> routes = List.of();
        private List<CapabilityDeclaration> capabilities = List.of();
        private List<PersonaDeclaration> personas = List.of();
        private boolean noLegacyMode = false;

        private Builder(String contractId, String name, String version, String productId) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
            this.productId = productId;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder routes(List<RouteDeclaration> routes) { this.routes = routes; return this; }
        public Builder capabilities(List<CapabilityDeclaration> capabilities) { this.capabilities = capabilities; return this; }
        public Builder personas(List<PersonaDeclaration> personas) { this.personas = personas; return this; }
        public Builder noLegacyMode(boolean noLegacyMode) { this.noLegacyMode = noLegacyMode; return this; }

        public ProductContract build() { return new ProductContract(this); }
    }
}
