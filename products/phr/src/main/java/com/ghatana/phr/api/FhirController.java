package com.ghatana.phr.api;

import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Thin API surface for PHR FHIR R4 create, read, and search operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class FhirController {

    private final PhrFhirR4Server server;

    public FhirController(PhrFhirR4Server server) {
        this.server = server;
    }

    public Promise<FhirApiResponse> createResource(String resourceType, String resourceJson) {
        return server.createResource(resourceType, resourceJson);
    }

    public Promise<FhirApiResponse> getResource(String resourceType, String resourceId) {
        return server.getResource(resourceType, resourceId);
    }

    public Promise<FhirApiResponse> searchResources(String resourceType, Map<String, String> searchParams) {
        return server.searchResources(resourceType, searchParams);
    }
}