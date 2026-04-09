package com.ghatana.phr.fhir.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.phr.fhir.FhirResourceService;
import com.ghatana.phr.fhir.FhirValidator;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.FhirResource;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.SearchResult;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.ValidationResult;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Shared implementation for typed FHIR resource providers backed by the FHIR plugin contracts
 * @doc.layer product
 * @doc.pattern Adapter
 */
abstract class AbstractFhirResourceProvider implements FhirResourceProvider {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final FhirResourceService resourceService;
    private final FhirValidator validator;

    protected AbstractFhirResourceProvider(FhirResourceService resourceService, FhirValidator validator) {
        this.resourceService = resourceService;
        this.validator = validator;
    }

    @Override
    public Promise<FhirResource> create(String resourceJson) {
        FhirResource resource = normalize(resourceJson, Instant.now());
        return validator.validateResource(resourceType(), resource.getJson())
            .then(validation -> ensureValid(validation, resource))
            .then(resourceService::storeResource)
            .map($ -> resource);
    }

    @Override
    public Promise<Optional<FhirResource>> read(String resourceId) {
        return resourceService.getResource(resourceId)
            .map(resource -> {
                if (resource == null || !resourceType().equals(resource.getResourceType())) {
                    return Optional.empty();
                }
                return Optional.of(resource);
            });
    }

    @Override
    public Promise<SearchResult> search(Map<String, String> searchParams) {
        Map<String, String> filtered = searchParams == null ? Map.of() : searchParams.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return resourceService.searchResources(resourceType(), filtered);
    }

    private Promise<FhirResource> ensureValid(ValidationResult validation, FhirResource resource) {
        if (!validation.isValid()) {
            return Promise.ofException(new IllegalArgumentException(validation.getMessage()));
        }
        return Promise.of(resource);
    }

    private FhirResource normalize(String resourceJson, Instant lastUpdated) {
        try {
            JsonNode parsed = JSON_MAPPER.readTree(resourceJson);
            if (!(parsed instanceof ObjectNode root)) {
                throw new IllegalArgumentException("FHIR resource payload must be a JSON object");
            }

            root.put("resourceType", resourceType());
            if (root.path("id").asText().isBlank()) {
                root.put("id", UUID.randomUUID().toString());
            }

            ObjectNode meta = root.has("meta") && root.get("meta") instanceof ObjectNode existingMeta
                ? existingMeta
                : root.putObject("meta");
            meta.put("lastUpdated", lastUpdated.toString());

            String id = root.path("id").asText();
            return new FhirResource(id, resourceType(), JSON_MAPPER.writeValueAsString(root), lastUpdated);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid FHIR JSON payload", exception);
        }
    }
}
