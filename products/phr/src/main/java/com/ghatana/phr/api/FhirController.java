package com.ghatana.phr.api;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Thin API surface for PHR FHIR R4 create, read, and search operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class FhirController implements KernelLifecycleAware {

    private final PhrFhirR4Server server;

    public FhirController(PhrFhirR4Server server) {
        this.server = server;
    }

    public Promise<PhrApiResponse> createResource(String resourceType, String resourceJson) {
        return server.createResource(resourceType, resourceJson);
    }

    public Promise<PhrApiResponse> getResource(String resourceType, String resourceId) {
        return server.getResource(resourceType, resourceId);
    }

    public Promise<PhrApiResponse> searchResources(String resourceType, Map<String, String> searchParams) {
        return server.searchResources(resourceType, searchParams);
    }

    @Override
    public Promise<Void> start() {
        return server.start();
    }

    @Override
    public Promise<Void> stop() {
        return server.stop();
    }

    @Override
    public boolean isHealthy() {
        return server.isHealthy();
    }

    @Override
    public String getName() {
        return "FhirController";
    }
}
