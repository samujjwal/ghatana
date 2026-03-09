package com.ghatana.yappc.api.testing.dto;

/**
 * CoverageGap.
 *
 * @doc.type record
 * @doc.purpose coverage gap
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CoverageGap(
    String file,
    String type,
    String description,
    String severity
) {}
