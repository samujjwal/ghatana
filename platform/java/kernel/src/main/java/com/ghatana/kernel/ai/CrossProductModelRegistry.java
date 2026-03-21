package com.ghatana.kernel.ai;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-product model registry with PII/bias/compliance validation.
 *
 * <p>Manages ML models that can be shared across products with proper governance:
 * <ul>
 *   <li>PII/data leakage validation before cross-product sharing</li>
 *   <li>Bias and fairness auditing for regulated domains</li>
 *   <li>Regulatory compliance checking (finance, healthcare)</li>
 *   <li>Version management with A/B rollout support</li>
 * </ul></p>
 *
 * @deprecated This class uses product id strings ({@code sourceProduct}, {@code targetProducts})
 *             which violate kernel purity. A scope-aware model registry should use
 *             {@link com.ghatana.kernel.scope.ScopeDescriptor} for scope identification.
 *             Per KERNEL_CANONICALIZATION_DECISIONS.md Day 10 cleanup.
 *
 * @doc.type class
 * @doc.purpose Cross-product ML model registry with privacy, fairness, and compliance validation
 * @doc.layer core
 * @doc.pattern Service, Registry
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@Deprecated(forRemoval = true)
public class CrossProductModelRegistry {

    private final Map<String, ProductModelRegistry> productRegistries;
    private final Map<String, CrossProductModel> sharedModels;
    private final DataCloudKernelAdapter dataCloud;
    private final ValidationService validationService;

    public CrossProductModelRegistry(DataCloudKernelAdapter dataCloud, ValidationService validationService) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud cannot be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.productRegistries = new ConcurrentHashMap<>();
        this.sharedModels = new ConcurrentHashMap<>();
    }

    /**
     * Registers a model for cross-product usage with validation.
     *
     * @param model the cross-product model
     * @return Promise completing when registration is done
     */
    public Promise<ValidationResult> registerCrossProductModel(CrossProductModel model) {
        // Validate model for cross-product usage
        return validateCrossProductModel(model)
            .then(validationResult -> {
                if (!validationResult.isValid()) {
                    return Promise.of(validationResult);
                }

                // Store model in Data-Cloud
                return dataCloud.storeModel(model)
                    .then($ -> {
                        // Register in shared models
                        sharedModels.put(model.getModelId(), model);

                        // Register in target product registries
                        for (String productId : model.getTargetProducts()) {
                            productRegistries
                                .computeIfAbsent(productId, k -> new ProductModelRegistry())
                                .registerModel(model);
                        }

                        return Promise.of(validationResult);
                    });
            });
    }

    /**
     * Validates a model for cross-product usage.
     */
    private Promise<ValidationResult> validateCrossProductModel(CrossProductModel model) {
        List<ValidationError> errors = new ArrayList<>();

        // Check for PII/data leakage risks
        ValidationResult piiResult = validationService.validateDataPrivacy(model);
        if (!piiResult.isValid()) {
            errors.addAll(piiResult.getErrors());
        }

        // Check for bias/fairness
        ValidationResult biasResult = validationService.validateFairness(model);
        if (!biasResult.isValid()) {
            errors.addAll(biasResult.getErrors());
        }

        // Check for regulatory compliance
        ValidationResult complianceResult = validationService.validateCompliance(model);
        if (!complianceResult.isValid()) {
            errors.addAll(complianceResult.getErrors());
        }

        return Promise.of(new ValidationResult(errors.isEmpty(), errors));
    }

    /**
     * Gets a shared model by ID.
     */
    public Optional<CrossProductModel> getModel(String modelId) {
        return Optional.ofNullable(sharedModels.get(modelId));
    }

    /**
     * Gets all models available to a product.
     */
    public Set<CrossProductModel> getModelsForProduct(String productId) {
        ProductModelRegistry registry = productRegistries.get(productId);
        return registry != null ? registry.getAllModels() : Set.of();
    }

    /**
     * Checks if a model is shared with a product.
     */
    public boolean isModelAvailableToProduct(String modelId, String productId) {
        CrossProductModel model = sharedModels.get(modelId);
        if (model == null) {
            return false;
        }
        return model.getTargetProducts().contains(productId);
    }

    // ==================== Inner Classes ====================

    /**
     * Cross-product ML model definition.
     */
    public static class CrossProductModel {
        private final String modelId;
        private final String name;
        private final String version;
        private final String sourceProduct;
        private final Set<String> targetProducts;
        private final ModelType type;
        private final Map<String, Object> metadata;
        private final Instant createdAt;

        public CrossProductModel(String modelId, String name, String version,
                                  String sourceProduct, Set<String> targetProducts,
                                  ModelType type, Map<String, Object> metadata) {
            this.modelId = modelId;
            this.name = name;
            this.version = version;
            this.sourceProduct = sourceProduct;
            this.targetProducts = Set.copyOf(targetProducts);
            this.type = type;
            this.metadata = Map.copyOf(metadata);
            this.createdAt = Instant.now();
        }

        public String getModelId() { return modelId; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getSourceProduct() { return sourceProduct; }
        public Set<String> getTargetProducts() { return targetProducts; }
        public ModelType getType() { return type; }
        public Map<String, Object> getMetadata() { return metadata; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public enum ModelType {
        CLASSIFICATION,
        REGRESSION,
        GENERATIVE,
        EMBEDDING,
        ANOMALY_DETECTION
    }

    /**
     * Product-specific model registry.
     */
    private static class ProductModelRegistry {
        private final Map<String, CrossProductModel> models = new ConcurrentHashMap<>();

        void registerModel(CrossProductModel model) {
            models.put(model.getModelId(), model);
        }

        Set<CrossProductModel> getAllModels() {
            return Set.copyOf(models.values());
        }
    }

    /**
     * Validation result.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() { return valid; }
        public List<ValidationError> getErrors() { return errors; }
    }

    /**
     * Validation error.
     */
    public static class ValidationError {
        private final String code;
        private final String message;
        private final ErrorSeverity severity;

        public ValidationError(String code, String message, ErrorSeverity severity) {
            this.code = code;
            this.message = message;
            this.severity = severity;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
        public ErrorSeverity getSeverity() { return severity; }
    }

    public enum ErrorSeverity {
        WARNING,
        ERROR,
        CRITICAL
    }

    // Stub interfaces
    public interface DataCloudKernelAdapter {
        Promise<Void> storeModel(CrossProductModel model);
    }

    public interface ValidationService {
        ValidationResult validateDataPrivacy(CrossProductModel model);
        ValidationResult validateFairness(CrossProductModel model);
        ValidationResult validateCompliance(CrossProductModel model);
    }
}
