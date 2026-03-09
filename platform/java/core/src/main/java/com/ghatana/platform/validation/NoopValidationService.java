/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * No-operation implementation of ValidationService.
 *
 * Always returns successful validation results. Useful for testing
 * and fallback scenarios.
 *
 * @doc.type class
 * @doc.purpose No-op validation service that always passes
 * @doc.layer platform
 * @doc.pattern Service
 */
public class NoopValidationService implements ValidationService {

    @Override
    @NotNull
    public Promise<ValidationResult> validateEvent(Object event) {
        return Promise.of(ValidationResult.success());
    }

    @Override
    @NotNull
    public Promise<ValidationResult> validatePayload(Object eventType, String payload) {
        return Promise.of(ValidationResult.success());
    }

    @Override
    @NotNull
    public Promise<ValidationResult> validateSchema(String schema) {
        return Promise.of(ValidationResult.success());
    }

    @Override
    @NotNull
    public Promise<String> compileSchema(String schema) {
        return Promise.of("noop");
    }
}
