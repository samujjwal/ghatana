package com.ghatana.yappc.services.validate;

import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.PolicySpec;
import com.ghatana.yappc.domain.validate.ValidationConfig;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Pre-build validation with pluggable validators
 * @doc.layer service
 * @doc.pattern Service
 */
public interface ValidationService {
    /**
     * Validates shape specification using all registered validators.
     * 
     * @param spec The shape specification to validate
     * @return Promise of validation result
     */
    Promise<LifecycleValidationResult> validate(ShapeSpec spec);

    /**
     * Validates with fine-grained control over which validators to run.
     * 
     * @param spec The shape specification
     * @param config Configuration to include/exclude specific validators
     * @return Promise of validation result
     */
    Promise<LifecycleValidationResult> validate(ShapeSpec spec, ValidationConfig config);
    
    /**
     * Validates with custom policy constraints.
     * 
     * @param spec The shape specification
     * @param policy Custom validation policy
     * @return Promise of validation result
     */
    Promise<LifecycleValidationResult> validateWithPolicy(ShapeSpec spec, PolicySpec policy);
}
