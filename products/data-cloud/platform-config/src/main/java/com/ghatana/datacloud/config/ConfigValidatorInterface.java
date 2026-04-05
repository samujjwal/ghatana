/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.RawCollectionConfig;

/**
 * Interface for configuration validation.
 *
 * <p>Provides validation operations for Data Cloud collection configurations,
 * supporting both basic and multi-tenancy validation.
 *
 * @doc.type interface
 * @doc.purpose Contract for configuration validation
 * @doc.layer core
 * @doc.pattern Strategy, Validator
 */
public interface ConfigValidatorInterface {

    /**
     * Validate raw configuration and return validation result.
     *
     * @param config raw config to validate
     * @return validation result with errors and warnings
     */
    ConfigValidator.ValidationResult validate(RawCollectionConfig config);

    /**
     * Validate configuration and throw if invalid.
     *
     * @param config raw config to validate
     * @throws com.ghatana.platform.core.exception.ConfigurationException if validation fails
     */
    void validateOrFail(RawCollectionConfig config);

    /**
     * Validates multi-tenancy constraints for a configuration.
     *
     * @param config raw config to validate
     * @param expectedTenantId the expected tenant ID (from request context)
     * @return validation result with tenant-specific errors
     */
    ConfigValidator.ValidationResult validateTenancy(RawCollectionConfig config, String expectedTenantId);
}
