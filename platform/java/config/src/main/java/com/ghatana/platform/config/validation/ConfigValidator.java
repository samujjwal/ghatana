/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.validation;

import com.ghatana.platform.config.ConfigSource;
import com.ghatana.platform.core.validation.ValidationFramework;

/**
 * Validates configuration values.
 *
 * Implementations validate specific configuration constraints
 * and return validation results.
 *
 * @doc.type interface
 * @doc.purpose Validates configuration values against constraints
 * @doc.layer platform
 * @doc.pattern Service
 */
@FunctionalInterface
public interface ConfigValidator {

    /**
     * Validates a configuration value.
     *
     * @param key the configuration key
     * @param value the configuration value
     * @param config the configuration source
     * @return validation result
     */
    ValidationFramework.ValidationResult validate(String key, String value, ConfigSource config);
}
