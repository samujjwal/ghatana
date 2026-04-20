package com.ghatana.platform.testing.contract;

import java.util.*;

/**
 * @doc.type interface
 * @doc.purpose Defines the contract (routes, methods, parameters) for an API endpoint
 * @doc.layer platform
 * @doc.pattern Contract definition
 */
public interface ApiContractDefinition {
    /**
     * Get the base path prefix for this API contract (e.g., "/api/v1/phr" or "/api/v1/finance").
     */
    String getBasePath();

    /**
     * Get the full set of routes defined in the contract.
     * Each route should be in OpenAPI path format: "/resource/{id}"
     */
    Set<String> getDefinedRoutes();

    /**
     * Get the HTTP methods for a given route.
     */
    Set<String> getMethodsForRoute(String route);

    /**
     * Get the OpenAPI specification version string.
     */
    String getOpenApiVersion();

    /**
     * Get the contract version/tag (e.g., "v1", "v2024-04-19").
     */
    String getContractVersion();

    /**
     * Validate that a request matches this contract.
     * @return validation result with detailed mismatch information if invalid
     */
    ContractValidationResult validate(String method, String path);
}
