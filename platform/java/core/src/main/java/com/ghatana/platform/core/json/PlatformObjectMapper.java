/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;

/**
 * Canonical, pre-configured {@link ObjectMapper} for the Ghatana platform.
 *
 * <p>All modules should use {@link #instance()} instead of constructing their own
 * {@code ObjectMapper}. This guarantees consistent serialization behaviour for
 * {@code Instant}, {@code LocalDate}, {@code ZonedDateTime}, {@code Optional}, and
 * other JDK types across the entire monorepo.
 *
 * <h3>Pre-registered modules</h3>
 * <ul>
 *   <li>{@link JavaTimeModule} — ISO-8601 date/time serialization</li>
 *   <li>{@link Jdk8Module} — {@code Optional} support</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>Dates serialized as ISO-8601 strings (not numeric timestamps)</li>
 *   <li>Unknown JSON properties ignored on deserialization (forward-compatible)</li>
 * </ul>
 *
 * <p>The singleton is thread-safe and should not be reconfigured after first access.
 * If a module needs custom settings, use {@link #copy()} to obtain an independent clone.
 *
 * @doc.type class
 * @doc.purpose Platform-canonical Jackson ObjectMapper singleton
 * @doc.layer core
 * @doc.pattern Singleton
 */
public final class PlatformObjectMapper {

    private static final ObjectMapper INSTANCE = createMapper();

    private PlatformObjectMapper() {
        // utility class
    }

    /**
     * Returns the shared, pre-configured {@link ObjectMapper} singleton.
     *
     * <p>Do not reconfigure the returned instance — it is shared across the entire JVM.
     *
     * @return the platform-canonical ObjectMapper
     */
    @NotNull
    public static ObjectMapper instance() {
        return INSTANCE;
    }

    /**
     * Returns an independent copy of the platform ObjectMapper for cases that need
     * custom settings (e.g. a product registering additional modules).
     *
     * @return a mutable copy of the platform ObjectMapper
     */
    @NotNull
    public static ObjectMapper copy() {
        return INSTANCE.copy();
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
