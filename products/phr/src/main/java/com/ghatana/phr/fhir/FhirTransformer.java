/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.fhir;

import io.activej.promise.Promise;

/**
 * Contract for transforming data between internal PHR format and FHIR R4.
 *
 * <p>Exported by {@code FhirInteropKernelPlugin} for cross-plugin consumption
 * within the PHR kernel. Supports bidirectional transformation.
 *
 * @doc.type interface
 * @doc.purpose FHIR R4 bidirectional data transformation contract
 * @doc.layer product
 * @doc.pattern Port
 */
public interface FhirTransformer {

    /**
     * Transforms internal PHR data to FHIR R4 format.
     *
     * @param internalData       the internal data object
     * @param targetResourceType the target FHIR resource type
     * @return Promise containing FHIR JSON string
     */
    Promise<String> transformToFhir(Object internalData, String targetResourceType);

    /**
     * Transforms FHIR R4 resource JSON to internal PHR format.
     *
     * @param fhirJson           the FHIR JSON string
     * @param sourceResourceType the source FHIR resource type
     * @return Promise containing the internal data object
     */
    Promise<Object> transformFromFhir(String fhirJson, String sourceResourceType);
}
