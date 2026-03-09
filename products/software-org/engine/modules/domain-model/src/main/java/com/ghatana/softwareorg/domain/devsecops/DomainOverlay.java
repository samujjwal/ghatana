package com.ghatana.softwareorg.domain.devsecops;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * DomainOverlay captures domain-specific overlays (e.g., PCI-DSS, HIPAA) that
 * add stricter acceptance criteria or references on top of a base DevSecOps
 * agent definition.
 */
@Value
@Builder
public class DomainOverlay {

    String domainId;

    @Singular("extraAcceptanceCriterion")
    List<String> extraAcceptanceCriteria;

    @Singular("extraReference")
    List<String> extraReferences;

    String notes;
}
