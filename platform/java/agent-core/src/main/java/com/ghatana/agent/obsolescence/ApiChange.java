/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a change in an API that may cause obsolescence.
 *
 * @doc.type record
 * @doc.purpose Value object for API changes
 * @doc.layer agent-core
 * @doc.pattern Value Object
 */
public record ApiChange(
        @NotNull String apiName,
        @NotNull String endpoint,
        @NotNull ChangeType changeType,
        @NotNull String description,
        @NotNull String previousSignature,
        @NotNull String newSignature
) {
    public ApiChange {
        Objects.requireNonNull(apiName, "apiName must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(changeType, "changeType must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(previousSignature, "previousSignature must not be null");
        Objects.requireNonNull(newSignature, "newSignature must not be null");
    }

    /**
     * Types of API changes.
     */
    public enum ChangeType {
        ENDPOINT_REMOVED,
        PARAMETER_REMOVED,
        PARAMETER_TYPE_CHANGED,
        RETURN_TYPE_CHANGED,
        AUTHENTICATION_CHANGED,
        RATE_LIMIT_CHANGED,
        BREAKING_CHANGE,
        DEPRECATED
    }

    /**
     * Creates an API change for a removed endpoint.
     */
    @NotNull
    public static ApiChange endpointRemoved(
            @NotNull String apiName,
            @NotNull String endpoint,
            @NotNull String previousSignature) {
        return new ApiChange(
                apiName,
                endpoint,
                ChangeType.ENDPOINT_REMOVED,
                "Endpoint removed",
                previousSignature,
                ""
        );
    }

    /**
     * Creates an API change for a parameter change.
     */
    @NotNull
    public static ApiChange parameterChanged(
            @NotNull String apiName,
            @NotNull String endpoint,
            @NotNull String previousSignature,
            @NotNull String newSignature) {
        return new ApiChange(
                apiName,
                endpoint,
                ChangeType.PARAMETER_TYPE_CHANGED,
                "Parameter type changed",
                previousSignature,
                newSignature
        );
    }

    /**
     * Creates an API change for a deprecated endpoint.
     */
    @NotNull
    public static ApiChange deprecated(
            @NotNull String apiName,
            @NotNull String endpoint,
            @NotNull String reason) {
        return new ApiChange(
                apiName,
                endpoint,
                ChangeType.DEPRECATED,
                reason,
                "",
                ""
        );
    }
}
