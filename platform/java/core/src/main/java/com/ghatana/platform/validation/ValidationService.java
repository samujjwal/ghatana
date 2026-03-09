/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Central validation service interface.
 *
 * Provides async validation operations for events, payloads, and schemas.
 *
 * @doc.type interface
 * @doc.purpose Service contract for validating domain objects
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ValidationService {

    /**
     * Validates an event asynchronously.
     *
     * @param event the event to validate
     * @return a Promise containing the validation result
     */
    @NotNull
    Promise<ValidationResult> validateEvent(@NotNull Object event);

    /**
     * Validates a payload against an event type.
     *
     * @param eventType the event type
     * @param payload the payload to validate
     * @return a Promise containing the validation result
     */
    @NotNull
    Promise<ValidationResult> validatePayload(@NotNull Object eventType, @NotNull String payload);

    /**
     * Validates a schema definition.
     *
     * @param schema the schema to validate
     * @return a Promise containing the validation result
     */
    @NotNull
    Promise<ValidationResult> validateSchema(@NotNull String schema);

    /**
     * Compiles a schema for efficient reuse.
     *
     * @param schema the schema to compile
     * @return a Promise containing the compiled schema
     */
    @NotNull
    Promise<String> compileSchema(@NotNull String schema);
}
