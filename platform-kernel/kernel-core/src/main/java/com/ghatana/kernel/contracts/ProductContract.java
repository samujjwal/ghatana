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
     * Declares a product route with entitlement metadata.
     */
    public record RouteDeclaration(
            String id,
            String path,
            String method,
            List<String> requiredRoles,
            List<String> requiredCapabilities,
            Map<String, String> metadata) {
        public RouteDeclaration {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(path, "path required");
            Objects.requireNonNull(method, "method required");
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

    private ProductContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              KernelContract.ContractFamily.API, builder.metadata);
        this.routes = builder.routes != null ? List.copyOf(builder.routes) : List.of();
        this.capabilities = builder.capabilities != null ? List.copyOf(builder.capabilities) : List.of();
        this.personas = builder.personas != null ? List.copyOf(builder.personas) : List.of();
        this.productId = builder.productId;
        validate();
    }

    public List<RouteDeclaration> getRoutes() { return routes; }
    public List<CapabilityDeclaration> getCapabilities() { return capabilities; }
    public List<PersonaDeclaration> getPersonas() { return personas; }
    public String getProductId() { return productId; }

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

        public ProductContract build() { return new ProductContract(this); }
    }
}
