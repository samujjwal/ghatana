package com.ghatana.phr.fhir.server;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Registry for typed FHIR resource providers exposed by the PHR runtime
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class FhirResourceProviderRegistry {

    private final Map<String, FhirResourceProvider> providers;

    public FhirResourceProviderRegistry(FhirResourceProvider... providers) {
        this.providers = Arrays.stream(providers)
            .collect(Collectors.toUnmodifiableMap(FhirResourceProvider::resourceType, Function.identity()));
    }

    public Optional<FhirResourceProvider> find(String resourceType) {
        return Optional.ofNullable(providers.get(resourceType));
    }

    public Set<String> supportedResourceTypes() {
        return providers.keySet();
    }
}