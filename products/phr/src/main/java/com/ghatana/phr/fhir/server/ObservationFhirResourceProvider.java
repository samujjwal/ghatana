package com.ghatana.phr.fhir.server;

import com.ghatana.phr.fhir.FhirResourceService;
import com.ghatana.phr.fhir.FhirValidator;

/**
 * @doc.type class
 * @doc.purpose Typed Observation provider for the PHR FHIR R4 runtime
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ObservationFhirResourceProvider extends AbstractFhirResourceProvider {

    public ObservationFhirResourceProvider(FhirResourceService resourceService, FhirValidator validator) {
        super(resourceService, validator);
    }

    @Override
    public String resourceType() {
        return "Observation";
    }
}
