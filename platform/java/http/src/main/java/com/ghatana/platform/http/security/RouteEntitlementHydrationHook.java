package com.ghatana.platform.http.security;

import java.util.Map;
import java.util.Objects;

/**
 * Kernel-owned hook for hydrating route entitlements from backend.
 * <p>
 * Products can implement this hook to fetch route entitlement metadata from their
 * backend endpoints and hydrate the frontend with server-side authorization decisions.
 * This ensures visibility decisions are made by the backend, not the frontend.
 * </p>
 *
 * @doc.type interface
 * @doc.purpose Hook for hydrating route entitlements from backend
 * @doc.layer kernel
 * @doc.pattern Hook
 */
public interface RouteEntitlementHydrationHook {

    /**
     * Hydrates route entitlements for the current user context.
     *
     * @param tenantId the tenant ID
     * @param principalId the principal/user ID
     * @param role the current role
     * @param persona the current persona (optional)
     * @param tier the current commercial tier (optional)
     * @param correlationId the correlation ID for tracing (optional)
     * @return hydrated product route entitlement
     * @throws IllegalStateException if hydration fails
     */
    ProductRouteEntitlement hydrate(
            String tenantId,
            String principalId,
            String role,
            String persona,
            String tier,
            String correlationId);

    /**
     * Default no-op implementation for products without backend entitlement endpoints.
     * Products should replace this with their own implementation that fetches from backend.
     */
    static RouteEntitlementHydrationHook noOp() {
        return new RouteEntitlementHydrationHook() {
            @Override
            public ProductRouteEntitlement hydrate(
                    String tenantId,
                    String principalId,
                    String role,
                    String persona,
                    String tier,
                    String correlationId) {
                throw new IllegalStateException(
                    "Route entitlement hydration unavailable. " +
                    "Products must implement RouteEntitlementHydrationHook to fetch entitlements from backend. " +
                    "Frontend-only visibility decisions are not allowed."
                );
            }
        };
    }

    /**
     * Builder for creating hydration hooks with configuration.
     */
    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private String entitlementEndpoint;
        private Map<String, String> headers;

        public Builder entitlementEndpoint(String endpoint) {
            this.entitlementEndpoint = endpoint;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public RouteEntitlementHydrationHook build() {
            Objects.requireNonNull(entitlementEndpoint, "entitlementEndpoint is required");
            // In a real implementation, this would create an HTTP client
            // that fetches entitlements from the configured endpoint
            return noOp();
        }
    }
}
