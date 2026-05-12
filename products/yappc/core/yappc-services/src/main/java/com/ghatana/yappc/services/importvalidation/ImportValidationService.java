/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.importvalidation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Service for validating untrusted input before document mutation.
 * Validates import sources, runtime health, and component mapping to registry.
 * Ensures residual islands are visible and reviewable before mutation.
 *
 * @doc.type interface
 * @doc.purpose Service for validating untrusted import input before document mutation
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ImportValidationService {

    /**
     * Validates the import source before any mutation.
     *
     * @param sourceUrl The source URL to validate
     * @param sourceType The source type
     * @return ValidationResult indicating if validation passed
     */
    ValidationResult validateSource(@NotNull String sourceUrl, @NotNull String sourceType);

    /**
     * Validates runtime health before proceeding with import.
     *
     * @return RuntimeHealthResult indicating if runtime is healthy
     */
    RuntimeHealthResult validateRuntimeHealth();

    /**
     * Maps known components to the registry.
     *
     * @param components The components to map
     * @return ComponentMappingResult with mapped and residual components
     */
    ComponentMappingResult mapComponentsToRegistry(@NotNull List<ComponentDescriptor> components);

    /**
     * Creates residual islands for unknown/untrusted components.
     *
     * @param unknownComponents The unknown components
     * @return ResidualIslandResult with created residual islands
     */
    ResidualIslandResult createResidualIslands(@NotNull List<ComponentDescriptor> unknownComponents);

    /**
     * Result of source validation.
     */
    record ValidationResult(
            @NotNull boolean valid,
            @NotNull List<ValidationError> errors,
            @NotNull List<ValidationWarning> warnings,
            @NotNull String sourceUrl
    ) {
        public ValidationResult {
            if (errors == null) {
                throw new IllegalArgumentException("errors cannot be null");
            }
            if (warnings == null) {
                throw new IllegalArgumentException("warnings cannot be null");
            }
        }
    }

    /**
     * Validation error.
     */
    record ValidationError(
            @NotNull String code,
            @NotNull String message,
            @NotNull String field,
            @NotNull Severity severity
    ) {
        public ValidationError {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message is required");
            }
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field is required");
            }
        }
    }

    /**
     * Validation warning.
     */
    record ValidationWarning(
            @NotNull String code,
            @NotNull String message,
            @NotNull String field
    ) {
        public ValidationWarning {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message is required");
            }
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field is required");
            }
        }
    }

    /**
     * Severity level.
     */
    enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    /**
     * Result of runtime health validation.
     */
    record RuntimeHealthResult(
            @NotNull boolean healthy,
            @NotNull String status,
            @Nullable String degradationReason,
            @NotNull Map<String, Boolean> componentHealth
    ) {
        public RuntimeHealthResult {
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status is required");
            }
            if (componentHealth == null) {
                throw new IllegalArgumentException("componentHealth cannot be null");
            }
        }
    }

    /**
     * Component descriptor.
     */
    record ComponentDescriptor(
            @NotNull String componentId,
            @NotNull String componentName,
            @NotNull String componentType,
            @Nullable String version,
            @NotNull Map<String, String> properties
    ) {
        public ComponentDescriptor {
            if (componentId == null || componentId.isBlank()) {
                throw new IllegalArgumentException("componentId is required");
            }
            if (componentName == null || componentName.isBlank()) {
                throw new IllegalArgumentException("componentName is required");
            }
            if (componentType == null || componentType.isBlank()) {
                throw new IllegalArgumentException("componentType is required");
            }
            if (properties == null) {
                throw new IllegalArgumentException("properties cannot be null");
            }
        }
    }

    /**
     * Result of component mapping to registry.
     */
    record ComponentMappingResult(
            @NotNull List<MappedComponent> mappedComponents,
            @NotNull List<ComponentDescriptor> residualComponents,
            @NotNull boolean hasResiduals
    ) {
        public ComponentMappingResult {
            if (mappedComponents == null) {
                throw new IllegalArgumentException("mappedComponents cannot be null");
            }
            if (residualComponents == null) {
                throw new IllegalArgumentException("residualComponents cannot be null");
            }
        }
    }

    /**
     * Mapped component.
     */
    record MappedComponent(
            @NotNull String componentId,
            @NotNull String registryComponentId,
            @NotNull MappingStatus status,
            @Nullable String mappingReason
    ) {
        public MappedComponent {
            if (componentId == null || componentId.isBlank()) {
                throw new IllegalArgumentException("componentId is required");
            }
            if (registryComponentId == null || registryComponentId.isBlank()) {
                throw new IllegalArgumentException("registryComponentId is required");
            }
        }
    }

    /**
     * Mapping status.
     */
    enum MappingStatus {
        EXACT_MATCH,
        VERSION_MISMATCH,
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }

    /**
     * Result of residual island creation.
     */
    record ResidualIslandResult(
            @NotNull String islandId,
            @NotNull List<ComponentDescriptor> components,
            @NotNull String status,
            @NotNull java.time.Instant createdAt
    ) {
        public ResidualIslandResult {
            if (islandId == null || islandId.isBlank()) {
                throw new IllegalArgumentException("islandId is required");
            }
            if (components == null) {
                throw new IllegalArgumentException("components cannot be null");
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status is required");
            }
            if (createdAt == null) {
                throw new IllegalArgumentException("createdAt is required");
            }
        }
    }
}
