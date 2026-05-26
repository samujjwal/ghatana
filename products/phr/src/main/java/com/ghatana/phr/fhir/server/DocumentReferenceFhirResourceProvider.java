package com.ghatana.phr.fhir.server;

import com.ghatana.phr.fhir.FhirResourceService;
import com.ghatana.phr.fhir.FhirValidator;

/**
 * @doc.type class
 * @doc.purpose Typed DocumentReference provider for the PHR FHIR R4 runtime
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DocumentReferenceFhirResourceProvider extends AbstractFhirResourceProvider {

    public DocumentReferenceFhirResourceProvider(FhirResourceService resourceService, FhirValidator validator) {
        super(resourceService, validator);
    }

    @Override
    public String resourceType() {
        return "DocumentReference";
    }
}
