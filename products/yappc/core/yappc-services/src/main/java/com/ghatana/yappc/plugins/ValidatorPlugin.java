package com.ghatana.yappc.plugins;

import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.ValidationIssue;
import io.activej.promise.Promise;

import java.util.List;

/**
 * @doc.type interface
 * @doc.purpose Plugin interface for custom validators
 * @doc.layer plugin
 * @doc.pattern SPI
 */
public interface ValidatorPlugin extends YappcPlugin {
    
    /**
     * Gets the validator ID.
     * 
     * @return Unique validator identifier
     */
    String getValidatorId();
    
    /**
     * Gets the validator category.
     * 
     * @return Validator category (e.g., "security", "performance")
     */
    String getCategory();
    
    /**
     * Validates a shape specification.
     * 
     * @param spec Shape specification to validate
     * @return Promise of validation issues (empty if valid)
     */
    Promise<List<ValidationIssue>> validate(ShapeSpec spec);
    
    /**
     * Checks if this validator is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    default boolean isEnabled() {
        return true;
    }
}
