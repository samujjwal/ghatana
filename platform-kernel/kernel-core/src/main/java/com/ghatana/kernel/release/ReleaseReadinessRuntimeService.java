package com.ghatana.kernel.release;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Kernel runtime API for product release readiness evidence.
 *
 * <p>This service provides runtime access to release readiness evidence
 * for products, abstracting away the storage mechanism (file parsing,
 * database, etc.) and allowing products to query readiness state
 * without direct filesystem access.</p>
 *
 * @doc.type interface
 * @doc.purpose Kernel runtime API for product release readiness evidence
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ReleaseReadinessRuntimeService {

    /**
     * Get release readiness evidence for a product.
     *
     * @param productId The product ID (e.g., "product-id")
     * @param environment The environment (e.g., "staging", "prod")
     * @return Promise containing the release readiness evidence as a Map
     */
    Promise<Map<String, Object>> getReleaseReadiness(String productId, String environment);

    /**
     * Get a specific section of release readiness evidence.
     *
     * @param productId The product ID
     * @param environment The environment
     * @param sectionId The section ID (e.g., "evidenceFreshness", "fhirRuntime")
     * @return Promise containing the section data as a Map
     */
    Promise<Map<String, Object>> getReleaseReadinessSection(String productId, String environment, String sectionId);
}
