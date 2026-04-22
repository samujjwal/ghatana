package com.ghatana.datacloud.plugins.validation;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Validation Processor - Uses pattern detection for data validation.
 *
 * <p>This processor is intentionally self-contained inside Data Cloud. Advanced
 * or product-specific validation may consume its results, but the foundational
 * validation path must not discover or prefer higher-level product runtimes.
 *
 * @doc.type class
 * @doc.purpose Data validation using Data-Cloud-owned validation strategies
 * @doc.layer plugin
 * @doc.pattern Strategy
 * @since 1.0.0
 */
public class DataValidationProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataValidationProcessor.class);

    private final ValidationStrategy validationStrategy;

    public DataValidationProcessor() {
        this(new BasicValidationStrategy());
        log.info("DataValidationProcessor initialized with Data-Cloud basic validation strategy");
    }

    public DataValidationProcessor(ValidationStrategy strategy) {
        this.validationStrategy = Objects.requireNonNull(strategy, "strategy required");
    }

    /**
     * Validate entity data before save.
     *
     * @param tenant tenant context
     * @param entity entity to validate
     * @return promise of validation result
     */
    public Promise<ValidationResult> validate(TenantContext tenant, EntityStore.Entity entity) {
        return validationStrategy.validate(tenant, entity);
    }

    /**
     * Validate entity data with specific rules.
     *
     * @param tenant tenant context
     * @param collection collection name
     * @param data entity data
     * @param rules validation rules
     * @return promise of validation result
     */
    public Promise<ValidationResult> validateWithRules(
            TenantContext tenant,
            String collection,
            Map<String, Object> data,
            List<ValidationRule> rules) {
        return validationStrategy.validateWithRules(tenant, collection, data, rules);
    }

    /**
     * Detect patterns in entity data.
     *
     * @param tenant tenant context
     * @param entity entity to analyze
     * @return promise of detected patterns
     */
    public Promise<List<DetectedPattern>> detectPatterns(TenantContext tenant, EntityStore.Entity entity) {
        return validationStrategy.detectPatterns(tenant, entity);
    }

    /**
     * Returns whether a higher-level product runtime was discovered.
     *
     * <p>Compatibility shim retained for older callers. Data Cloud no longer
     * auto-discovers AEP-specific validation strategies, so this always returns
     * {@code false}.
     */
    @Deprecated(forRemoval = false)
    public boolean isAepAvailable() {
        return false;
    }

    /**
     * Validation strategy interface.
     */
    public interface ValidationStrategy {
        Promise<ValidationResult> validate(TenantContext tenant, EntityStore.Entity entity);
        Promise<ValidationResult> validateWithRules(
            TenantContext tenant, String collection, Map<String, Object> data, List<ValidationRule> rules);
        Promise<List<DetectedPattern>> detectPatterns(TenantContext tenant, EntityStore.Entity entity);
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean valid,
        List<ValidationError> errors,
        Map<String, Object> metadata
    ) {
        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }

        public boolean passed() {
            return valid;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), Map.of());
        }

        public static ValidationResult failed(List<ValidationError> errors) {
            return new ValidationResult(false, errors, Map.of());
        }

        public static ValidationResult failed(String message) {
            return new ValidationResult(false, List.of(new ValidationError("VALIDATION_FAILED", message, null)), Map.of());
        }
    }

    /**
     * Validation error.
     */
    public record ValidationError(
        String code,
        String message,
        String field
    ) {}

    /**
     * Validation rule.
     */
    public record ValidationRule(
        String field,
        RuleType type,
        Object value,
        String message
    ) {
        public static ValidationRule required(String field) {
            return new ValidationRule(field, RuleType.REQUIRED, null, field + " is required");
        }

        public static ValidationRule minLength(String field, int min) {
            return new ValidationRule(field, RuleType.MIN_LENGTH, min, field + " must be at least " + min + " characters");
        }

        public static ValidationRule maxLength(String field, int max) {
            return new ValidationRule(field, RuleType.MAX_LENGTH, max, field + " must be at most " + max + " characters");
        }

        public static ValidationRule pattern(String field, String regex) {
            return new ValidationRule(field, RuleType.PATTERN, regex, field + " must match pattern");
        }
    }

    /**
     * Rule types.
     */
    public enum RuleType {
        REQUIRED, MIN_LENGTH, MAX_LENGTH, MIN_VALUE, MAX_VALUE, PATTERN, CUSTOM
    }

    /**
     * Detected pattern.
     */
    public record DetectedPattern(
        String patternId,
        String patternName,
        double confidence,
        Map<String, Object> details
    ) {}

    /**
     * Basic validation strategy (no AEP).
     */
    private static class BasicValidationStrategy implements ValidationStrategy {
        @Override
        public Promise<ValidationResult> validate(TenantContext tenant, EntityStore.Entity entity) {
            List<ValidationError> errors = new java.util.ArrayList<>();
            
            if (entity.data().isEmpty()) {
                errors.add(new ValidationError("EMPTY_DATA", "Entity data cannot be empty", null));
            }
            
            return Promise.of(errors.isEmpty() ? ValidationResult.success() : ValidationResult.failed(errors));
        }

        @Override
        public Promise<ValidationResult> validateWithRules(
                TenantContext tenant, String collection, Map<String, Object> data, List<ValidationRule> rules) {
            List<ValidationError> errors = new java.util.ArrayList<>();

            for (ValidationRule rule : rules) {
                Object value = data.get(rule.field());

                switch (rule.type()) {
                    case REQUIRED -> {
                        if (value == null || (value instanceof String s && s.isBlank())) {
                            errors.add(new ValidationError("REQUIRED", rule.message(), rule.field()));
                        }
                    }
                    case MIN_LENGTH -> {
                        if (value instanceof String s && s.length() < (Integer) rule.value()) {
                            errors.add(new ValidationError("MIN_LENGTH", rule.message(), rule.field()));
                        }
                    }
                    case MAX_LENGTH -> {
                        if (value instanceof String s && s.length() > (Integer) rule.value()) {
                            errors.add(new ValidationError("MAX_LENGTH", rule.message(), rule.field()));
                        }
                    }
                    case MIN_VALUE -> {
                        if (value instanceof Number n && n.doubleValue() < ((Number) rule.value()).doubleValue()) {
                            errors.add(new ValidationError("MIN_VALUE", rule.message(), rule.field()));
                        }
                    }
                    case MAX_VALUE -> {
                        if (value instanceof Number n && n.doubleValue() > ((Number) rule.value()).doubleValue()) {
                            errors.add(new ValidationError("MAX_VALUE", rule.message(), rule.field()));
                        }
                    }
                    case PATTERN -> {
                        if (value instanceof String s && !s.matches((String) rule.value())) {
                            errors.add(new ValidationError("PATTERN", rule.message(), rule.field()));
                        }
                    }
                    case CUSTOM -> {
                        // Custom validation would be handled by a custom strategy
                        // For basic strategy, we skip custom rules
                    }
                }
            }

            return Promise.of(errors.isEmpty() ? ValidationResult.success() : ValidationResult.failed(errors));
        }

        @Override
        public Promise<List<DetectedPattern>> detectPatterns(TenantContext tenant, EntityStore.Entity entity) {
            List<DetectedPattern> patterns = new java.util.ArrayList<>();
            
            // Detect common data patterns
            for (Map.Entry<String, Object> entry : entity.data().entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof String s) {
                    // Email pattern detection
                    if (s.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                        patterns.add(new DetectedPattern("email", "Email Address", 0.95, 
                            Map.of("field", field, "value", s)));
                    }
                    // URL pattern detection
                    if (s.matches("^https?://.*")) {
                        patterns.add(new DetectedPattern("url", "URL", 0.90,
                            Map.of("field", field, "value", s)));
                    }
                    // UUID pattern detection
                    if (s.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                        patterns.add(new DetectedPattern("uuid", "UUID", 0.98,
                            Map.of("field", field, "value", s)));
                    }
                }
            }
            
            return Promise.of(patterns);
        }
    }
}
