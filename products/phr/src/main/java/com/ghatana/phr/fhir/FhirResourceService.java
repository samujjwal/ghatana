/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.fhir;

import com.ghatana.phr.plugin.FhirInteropKernelPlugin.FhirResource;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.SearchResult;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Contract for FHIR R4 resource storage and retrieval operations.
 *
 * <p>Exported by {@code FhirInteropKernelPlugin} for cross-plugin consumption
 * within the PHR kernel. Implementations must be thread-safe and non-blocking.
 *
 * @doc.type interface
 * @doc.purpose FHIR R4 resource storage and retrieval contract
 * @doc.layer product
 * @doc.pattern Port
 */
public interface FhirResourceService {

    /**
     * Stores a FHIR resource.
     *
     * @param resource the FHIR resource to persist
     * @return Promise completing when stored
     */
    Promise<Void> storeResource(FhirResource resource);

    /**
     * Retrieves a FHIR resource by ID.
     *
     * @param resourceId the resource identifier
     * @return Promise containing the resource
     */
    Promise<FhirResource> getResource(String resourceId);

    /**
     * Searches FHIR resources by criteria.
     *
     * @param resourceType the resource type to search
     * @param searchParams the search parameters
     * @return Promise containing search results
     */
    Promise<SearchResult> searchResources(String resourceType, Map<String, String> searchParams);
}
