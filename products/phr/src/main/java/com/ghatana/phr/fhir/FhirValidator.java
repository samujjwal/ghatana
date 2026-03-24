/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.fhir;

import com.ghatana.phr.plugin.FhirInteropKernelPlugin.ValidationResult;
import io.activej.promise.Promise;

/**
 * Contract for FHIR R4 resource validation.
 *
 * <p>Exported by {@code FhirInteropKernelPlugin} for cross-plugin consumption
 * within the PHR kernel. Validates resources against the FHIR R4 specification.
 *
 * @doc.type interface
 * @doc.purpose FHIR R4 resource validation contract
 * @doc.layer product
 * @doc.pattern Port
 */
public interface FhirValidator {

    /**
     * Validates a FHIR resource against R4 specification.
     *
     * @param resourceType the FHIR resource type (e.g. "Patient", "Observation")
     * @param resourceJson the resource JSON string
     * @return Promise containing validation result
     */
    Promise<ValidationResult> validateResource(String resourceType, String resourceJson);
}
