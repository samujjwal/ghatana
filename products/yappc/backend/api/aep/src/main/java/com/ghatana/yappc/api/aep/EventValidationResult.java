/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP — Event Validation Result
 */

package com.ghatana.yappc.api.aep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of an event schema validation.
 *
 * <p>Use the factory methods {@link #success()}, {@link #failure(List)}, and
 * {@link #error(String)} to construct instances.
 *
 * @doc.type class
 * @doc.purpose Value object for YAPPC AEP event schema validation results
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class EventValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final String errorMessage;
    
    private EventValidationResult(boolean valid, List<String> errors, String errorMessage) {
        this.valid = valid;
        this.errors = errors != null ? Collections.unmodifiableList(new ArrayList<>(errors)) : Collections.emptyList();
        this.errorMessage = errorMessage;
    }
    
    public static EventValidationResult success() {
        return new EventValidationResult(true, null, null);
    }
    
    public static EventValidationResult failure(List<String> errors) {
        return new EventValidationResult(false, errors, null);
    }
    
    public static EventValidationResult error(String errorMessage) {
        return new EventValidationResult(false, null, errorMessage);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
