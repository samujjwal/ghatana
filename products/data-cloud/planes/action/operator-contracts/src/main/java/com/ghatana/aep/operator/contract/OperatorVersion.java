package com.ghatana.aep.operator.contract;

/**
 * @doc.type record
 * @doc.purpose Represents an AEP operator semantic version
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OperatorVersion(String value) {

    public OperatorVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
