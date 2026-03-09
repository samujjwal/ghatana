/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.record.adapter;

import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.record.Record;

import java.util.Objects;

/**
 * Maps between the two parallel {@code RecordType} enums:
 * <ul>
 *   <li>{@link com.ghatana.datacloud.RecordType} — JPA persistence layer (rich enum with properties)</li>
 *   <li>{@link com.ghatana.datacloud.record.Record.RecordType} — trait-based domain layer (simple enum)</li>
 * </ul>
 *
 * <p>Both enums have the same 5 values (ENTITY, EVENT, TIMESERIES, DOCUMENT, GRAPH),
 * so mapping is by name. The JPA enum carries additional metadata (mutability,
 * streaming support, etc.) which is lost when converting to the trait enum.
 *
 * @doc.type class
 * @doc.purpose Maps between JPA and trait RecordType enums
 * @doc.layer product
 * @doc.pattern Mapper
 */
public final class RecordTypeMapper {

    private RecordTypeMapper() {
        // Utility class — no instantiation
    }

    /**
     * Converts a JPA {@link RecordType} to a trait {@link Record.RecordType}.
     *
     * @param jpaType the JPA record type (must not be null)
     * @return the corresponding trait record type
     * @throws NullPointerException     if jpaType is null
     * @throws IllegalArgumentException if no matching trait type exists
     */
    public static Record.RecordType toTrait(RecordType jpaType) {
        Objects.requireNonNull(jpaType, "jpaType must not be null");
        return Record.RecordType.valueOf(jpaType.name());
    }

    /**
     * Converts a trait {@link Record.RecordType} to a JPA {@link RecordType}.
     *
     * @param traitType the trait record type (must not be null)
     * @return the corresponding JPA record type
     * @throws NullPointerException     if traitType is null
     * @throws IllegalArgumentException if no matching JPA type exists
     */
    public static RecordType toJpa(Record.RecordType traitType) {
        Objects.requireNonNull(traitType, "traitType must not be null");
        return RecordType.valueOf(traitType.name());
    }
}
