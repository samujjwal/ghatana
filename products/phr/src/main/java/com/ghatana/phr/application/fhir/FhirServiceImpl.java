package com.ghatana.phr.application.fhir;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of FhirService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides FHIR R4 resource management operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class FhirServiceImpl implements FhirService {

    private final ConcurrentMap<String, FhirResource> resources = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<FhirResourceVersion>> resourceHistory = new ConcurrentHashMap<>();

    @Override
    public Promise<FhirResource> createResource(PatientOperationContext ctx, String resourceType, Map<String, Object> resource) {
        String resourceId = resourceType + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        FhirResource fhirResource = new FhirResource(
            resourceId,
            resourceType,
            "1",
            resource,
            Instant.now().toString(),
            Instant.now().toString()
        );

        resources.put(resourceId, fhirResource);
        
        // Add to history
        FhirResourceVersion version = new FhirResourceVersion(
            "1",
            Instant.now().toString(),
            resource,
            ctx.userId()
        );
        resourceHistory.put(resourceId, List.of(version));
        
        return Promise.of(fhirResource);
    }

    @Override
    public Promise<Optional<FhirResource>> getResource(PatientOperationContext ctx, String resourceType, String resourceId) {
        return Promise.of(Optional.ofNullable(resources.get(resourceId)));
    }

    @Override
    public Promise<FhirResource> updateResource(PatientOperationContext ctx, String resourceType, String resourceId, Map<String, Object> resource) {
        FhirResource existing = resources.get(resourceId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Resource not found: " + resourceId));
        }

        String newVersion = String.valueOf(Integer.parseInt(existing.version()) + 1);
        
        FhirResource updated = new FhirResource(
            resourceId,
            resourceType,
            newVersion,
            resource,
            existing.createdAt(),
            Instant.now().toString()
        );

        resources.put(resourceId, updated);
        
        // Add to history
        FhirResourceVersion version = new FhirResourceVersion(
            newVersion,
            Instant.now().toString(),
            resource,
            ctx.userId()
        );
        
        List<FhirResourceVersion> history = new java.util.ArrayList<>(resourceHistory.getOrDefault(resourceId, List.of()));
        history.add(version);
        resourceHistory.put(resourceId, history);
        
        return Promise.of(updated);
    }

    @Override
    public Promise<List<FhirResource>> searchResources(PatientOperationContext ctx, String resourceType, Map<String, String> parameters) {
        return Promise.of(resources.values().stream()
            .filter(r -> r.resourceType().equals(resourceType))
            .toList());
    }

    @Override
    public Promise<FhirResourceHistory> getResourceHistory(PatientOperationContext ctx, String resourceType, String resourceId) {
        List<FhirResourceVersion> versions = resourceHistory.getOrDefault(resourceId, List.of());
        FhirResourceHistory history = new FhirResourceHistory(resourceId, resourceType, versions);
        return Promise.of(history);
    }
}
