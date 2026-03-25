/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
 * If a module needs custom settings, use {@link #copy()} for a quick mutable clone,
 * or use {@link #builder()} for a fully-configured variant.
 *
 * @doc.type class
 * @doc.purpose Platform-canonical Jackson ObjectMapper singleton with variant-builder support
 * @doc.layer core
 * @doc.pattern Singleton, Builder
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
     * <p>Equivalent to {@code builder().build()} without any customization.
     *
     * @return a mutable copy of the platform ObjectMapper
     */
    @NotNull
    public static ObjectMapper copy() {
        return INSTANCE.copy();
    }

    /**
     * Returns a {@link MapperBuilder} seeded with the platform defaults.
     *
     * <p>Use this when you need a customized variant of the platform mapper without
     * modifying the shared singleton. For example:
     * <pre>{@code
     * ObjectMapper prettyMapper = PlatformObjectMapper.builder()
     *         .prettyPrint(true)
     *         .failOnUnknownProperties(true)
     *         .registerModule(new MyCustomModule())
     *         .build();
     * }</pre>
     *
     * @return a new {@code MapperBuilder} pre-configured with platform defaults
     */
    @NotNull
    public static MapperBuilder builder() {
        return new MapperBuilder();
    }

    /**
     * Fluent builder for creating customized variants of the platform {@link ObjectMapper}.
     *
     * <p>Starts from the platform defaults (ISO-8601 dates, unknown-properties-ignored)
     * and allows selective overrides. Each call to {@link #build()} produces a fresh,
     * independent {@link ObjectMapper} — the shared singleton is never modified.
     *
     * @doc.type class
     * @doc.purpose Fluent builder for platform ObjectMapper variants
     * @doc.layer core
     * @doc.pattern Builder
     */
    public static final class MapperBuilder {

        private boolean prettyPrint = false;
        private boolean failOnUnknownProperties = false;
        private boolean writeDatesAsTimestamps = false;
        private boolean failOnNullForPrimitives = false;
        private boolean serializationInclusion = false;
        private final List<Module> additionalModules = new ArrayList<>();

        private MapperBuilder() {
            // Use PlatformObjectMapper.builder() factory
        }

        /**
         * Enables or disables pretty-print (indented) output.
         *
         * @param enabled {@code true} to enable pretty-print
         * @return this builder
         */
        public MapperBuilder prettyPrint(boolean enabled) {
            this.prettyPrint = enabled;
            return this;
        }

        /**
         * Enables or disables failing on unknown JSON properties during deserialization.
         * Platform default is {@code false} (unknown properties are ignored).
         *
         * @param enabled {@code true} to fail on unknown properties
         * @return this builder
         */
        public MapperBuilder failOnUnknownProperties(boolean enabled) {
            this.failOnUnknownProperties = enabled;
            return this;
        }

        /**
         * Enables or disables writing dates as numeric timestamps.
         * Platform default is {@code false} (dates are written as ISO-8601 strings).
         *
         * @param enabled {@code true} to write dates as numeric timestamps
         * @return this builder
         */
        public MapperBuilder writeDatesAsTimestamps(boolean enabled) {
            this.writeDatesAsTimestamps = enabled;
            return this;
        }

        /**
         * Enables failing on null values for primitive types.
         * Platform default is {@code false}.
         *
         * @param enabled {@code true} to fail on null-for-primitive
         * @return this builder
         */
        public MapperBuilder failOnNullForPrimitives(boolean enabled) {
            this.failOnNullForPrimitives = enabled;
            return this;
        }

        /**
         * Registers an additional Jackson {@link Module} on the resulting mapper.
         *
         * @param module the module to register, must not be null
         * @return this builder
         */
        public MapperBuilder registerModule(@NotNull Module module) {
            this.additionalModules.add(module);
            return this;
        }

        /**
         * Builds a new, independent {@link ObjectMapper} with the configured settings.
         *
         * @return a new {@code ObjectMapper} variant based on platform defaults
         */
        @NotNull
        public ObjectMapper build() {
            ObjectMapper mapper = createMapper();

            if (prettyPrint) {
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
            }
            if (failOnUnknownProperties) {
                mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            }
            if (writeDatesAsTimestamps) {
                mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
            if (failOnNullForPrimitives) {
                mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            }
            for (Module mod : additionalModules) {
                mapper.registerModule(mod);
            }
            return mapper;
        }
    }

    // ── Internal factory ─────────────────────────────────────────────────────

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
