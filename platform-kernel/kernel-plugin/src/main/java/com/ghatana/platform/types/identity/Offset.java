package com.ghatana.platform.types.identity;

/**
 * @doc.type record
 * @doc.purpose Self-contained offset identifier for extracted kernel plugin storage APIs.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record Offset(String value) {

    public Offset {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Offset cannot be null or blank");
        }
    }

    public static Offset of(String value) {
        return new Offset(value);
    }

    public static Offset of(long value) {
        return new Offset(String.valueOf(value));
    }

    public static Offset zero() {
        return new Offset("0");
    }

    @Override
    public String toString() {
        return value;
    }
}