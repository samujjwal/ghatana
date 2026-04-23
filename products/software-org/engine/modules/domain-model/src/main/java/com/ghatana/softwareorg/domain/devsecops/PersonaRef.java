package com.ghatana.softwareorg.domain.devsecops;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * PersonaRef is the domain-level representation of a DevSecOps persona
 * involved in an agent.
  * @doc.type class
 * @doc.purpose Provides persona ref functionality.
 * @doc.layer product
 * @doc.pattern Component
*/
@Value
@Builder
public class PersonaRef {

    String id;
    String displayName;
    List<String> tags;
}
