package com.ghatana.phr.fhir.server;

import com.ghatana.phr.fhir.FhirResourceService;
import com.ghatana.phr.fhir.FhirValidator;

/**
 * @doc.type class
 * @doc.purpose Typed Immunization provider for the PHR FHIR R4 runtime
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ImmunizationFhirResourceProvider extends AbstractFhirResourceProvider {

    public ImmunizationFhirResourceProvider(FhirResourceService resourceService, FhirValidator validator) {
        super(resourceService, validator);
    }

    @Override
    public String resourceType() {
        return "Immunization";
    }
}