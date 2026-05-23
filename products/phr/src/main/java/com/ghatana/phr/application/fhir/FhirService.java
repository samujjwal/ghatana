package com.ghatana.phr.application.fhir;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for FHIR R4 handling workflow.
 *
 * @doc.type class
 * @doc.purpose Defines operations for validating, processing, and storing FHIR R4 resources (PHR-F1-006)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface FhirService {

    /**
     * Create a FHIR resource.
     *
     * @param ctx         operation context
     * @param resourceType FHIR resource type
     * @param resource    FHIR resource
     * @return the created resource
     */
    Promise<FhirResource> createResource(PatientOperationContext ctx, String resourceType, Map<String, Object> resource);

    /**
     * Fetch a FHIR resource by ID.
     *
     * @param ctx         operation context
     * @param resourceType FHIR resource type
     * @param resourceId  resource ID
     * @return optional FHIR resource
     */
    Promise<Optional<FhirResource>> getResource(PatientOperationContext ctx, String resourceType, String resourceId);

    /**
     * Update a FHIR resource.
     *
     * @param ctx         operation context
     * @param resourceType FHIR resource type
     * @param resourceId  resource ID
     * @param resource    FHIR resource
     * @return updated resource
     */
    Promise<FhirResource> updateResource(PatientOperationContext ctx, String resourceType, String resourceId, Map<String, Object> resource);

    /**
     * Search FHIR resources.
     *
     * @param ctx         operation context
     * @param resourceType FHIR resource type
     * @param parameters  search parameters
     * @return list of matching resources
     */
    Promise<List<FhirResource>> searchResources(PatientOperationContext ctx, String resourceType, Map<String, String> parameters);

    /**
     * Get resource history.
     *
     * @param ctx         operation context
     * @param resourceType FHIR resource type
     * @param resourceId  resource ID
     * @return resource history
     */
    Promise<FhirResourceHistory> getResourceHistory(PatientOperationContext ctx, String resourceType, String resourceId);

    // ── Response types ─────────────────────────────────────────────────────────

    record FhirResource(
        String resourceId,
        String resourceType,
        String version,
        Map<String, Object> data,
        String createdAt,
        String updatedAt
    ) {}

    record FhirResourceHistory(
        String resourceId,
        String resourceType,
        List<FhirResourceVersion> versions
    ) {}

    record FhirResourceVersion(
        String versionId,
        String timestamp,
        Map<String, Object> data,
        String changedBy
    ) {}
}
