package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;

/**
 * Specialized plugin for validation operations.
 * 
 * <p>Validator plugins perform validation checks on various YAPPC artifacts
 * such as canvases, configurations, code, etc.
 * 
 * <p>Example implementation:
 * <pre>{@code
 * public class SecurityValidatorPlugin implements ValidatorPlugin {
 *     
 *     @Override
 *     public Promise<ValidationResult> validate(ValidationContext ctx) {
 *         return Promise.of(ctx.getTarget())
 *             .map(target -> performSecurityChecks(target))
 *             .map(issues -> ValidationResult.builder()
 *                 .validatorId(getMetadata().getId())
 *                 .issues(issues)
 *                 .build());
 *     }
 *     
 *     @Override
 *     public PluginMetadata getMetadata() {
 *         return PluginMetadata.builder()
 *             .id("security-validator")
 *             .name("Security Validator")
 *             .version("1.0.0")
 *             .category("security")
 *             .build();
 *     }
 * }
 * }</pre>
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for validator plugin
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface ValidatorPlugin extends YAPPCPlugin {
    
    /**
     * Validates the target in the given context.
     * 
     * @param ctx the validation context
     * @return a Promise containing the validation result
     */
    Promise<ValidationResult> validate(ValidationContext ctx);
    
    /**
     * Returns the validator ID.
     * 
     * @return the validator ID
     */
    default String getValidatorId() {
        return getMetadata().getId();
    }
    
    /**
     * Returns the validator category.
     * 
     * @return the category (e.g., "security", "quality", "compliance")
     */
    default String getValidatorCategory() {
        return getMetadata().getCategory();
    }
}
