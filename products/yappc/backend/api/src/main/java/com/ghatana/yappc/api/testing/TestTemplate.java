package com.ghatana.yappc.api.testing;

/**
 * TestTemplate.
 *
 * @doc.type record
 * @doc.purpose test template
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestTemplate(
    String id,
    String name,
    String framework,
    String description
) {}
