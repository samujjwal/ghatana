package com.ghatana.phr.fhir.server;

import com.ghatana.phr.plugin.FhirInteropKernelPlugin.FhirResource;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.SearchResult;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Typed provider contract for FHIR R4 resource create, read, and search flows
 * @doc.layer product
 * @doc.pattern Port
 */
public interface FhirResourceProvider {

    String resourceType();

    Promise<FhirResource> create(String resourceJson);

    Promise<Optional<FhirResource>> read(String resourceId);

    Promise<SearchResult> search(Map<String, String> searchParams);
}