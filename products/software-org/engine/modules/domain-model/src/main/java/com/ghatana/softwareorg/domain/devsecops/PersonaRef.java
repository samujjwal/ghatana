package com.ghatana.softwareorg.domain.devsecops;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * PersonaRef is the domain-level representation of a DevSecOps persona
 * involved in an agent.
 */
@Value
@Builder
public class PersonaRef {

    String id;
    String displayName;
    List<String> tags;
}
