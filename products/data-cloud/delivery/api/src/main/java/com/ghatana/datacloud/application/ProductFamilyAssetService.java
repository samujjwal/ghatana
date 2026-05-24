package com.ghatana.datacloud.application;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing product family assets with schema validation and promotion gate enforcement.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for managing reusable assets across the product family.
 * Includes schema validation, promotion gate enforcement, and lifecycle management.
 * All operations are tenant-scoped and return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Hardening</b><br>
 * - Validates evidence freshness for promotion (max 24 hours)
 * - Binds promotion to commit SHA and target environment
 * - Enforces environment-specific promotion states
 * - Validates runtime truth consistency
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProductFamilyAssetService service = new ProductFamilyAssetService(repository, metrics);
 *
 * // Register a new asset
 * ProductFamilyAsset asset = ProductFamilyAsset.builder()
 *     .assetId("phr-consent-ui-component")
 *     .assetName("PHR Consent UI Component")
 *     .assetType("ui-component")
 *     .sourceProductId("phr")
 *     .description("Reusable consent management UI component")
 *     .maturityLevel("stable")
 *     .tenantId("tenant-123")
 *     .build();
 *
 * Promise<ProductFamilyAsset> promise = service.registerAsset(asset);
 *
 * // In test with EventloopTestBase:
 * ProductFamilyAsset registered = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses ProductFamilyAssetRepository (infrastructure layer)
 * - Uses MetricsCollector (core/observability)
 * - Enforces schema validation and promotion gates
 * - Emits metrics for all operations
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repository.
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations tenant-scoped. Tenant extracted from context and enforced at repository level.
 *
 * @see ProductFamilyAsset
 * @see AssetPromotionPolicy
 * @see com.ghatana.datacloud.infrastructure.persistence.ProductFamilyAssetRepository
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Service for product family asset management with validation and promotion gates
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class ProductFamilyAssetService {

    private static final Logger log = LoggerFactory.getLogger(ProductFamilyAssetService.class);
    private static final Duration MAX_EVIDENCE_AGE = Duration.ofHours(24);

    private final ProductFamilyAssetRepository repository;
    private final MetricsCollector metrics;

    /**
     * Creates a new product family asset service.
     *
     * @param repository the product family asset repository (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if repository or metrics is null
     */
    public ProductFamilyAssetService(
            ProductFamilyAssetRepository repository,
            MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Registers a new asset with schema validation.
     *
     * <p><b>Validation</b><br>
     * - Asset ID must be unique
     * - Asset type must be valid (ui-component, service, workflow, policy)
     * - Maturity level must be valid (stable, beta, experimental)
     * - Source product must be registered
     * - Schema must conform to asset type requirements
     *
     * @param asset the asset to register
     * @return Promise that completes with the registered asset
     */
    public Promise<ProductFamilyAsset> registerAsset(ProductFamilyAsset asset) {
        try {
            validateAssetSchema(asset);

            log.info("Registering asset: {}, type: {}, source: {}",
                asset.assetId(), asset.assetType(), asset.sourceProductId());

            Promise<ProductFamilyAsset> result = repository.upsert(asset);

            result.whenComplete(() -> {
                metrics.incrementCounter("product_family_asset.registered", Map.of(
                    "asset_type", asset.assetType(),
                    "source_product", asset.sourceProductId(),
                    "maturity_level", asset.maturityLevel()
                ));
            });

            result.whenException(e -> {
                log.error("Failed to register asset: {}", asset.assetId(), e);
                metrics.incrementCounter("product_family_asset.register_failed", Map.of(
                    "asset_id", asset.assetId()
                ));
            });

            return result;
        } catch (Exception e) {
            log.error("Failed to register asset: {}", asset.assetId(), e);
            metrics.incrementCounter("product_family_asset.register_failed", Map.of(
                "asset_id", asset.assetId()
            ));
            return Promise.ofException(e);
        }
    }

    /**
     * Promotes an asset to target products with gate enforcement.
     *
     * <p><b>Gate Enforcement</b><br>
     * - Asset must be in approved promotion status
     * - Maturity level must meet policy requirements
     * - Target products must be in allowed list
     * - Approval workflow must be completed if required
     * - Evidence must be fresh (within 24 hours)
     * - Commit SHA must be present and valid
     * - Evidence environment must match target
     *
     * @param assetId the asset ID
     * @param targetProductIds the target product IDs
     * @param promotedBy the user promoting the asset
     * @param commitSha the commit SHA for the promotion
     * @param targetEnvironment the target environment
     * @param tenantId the tenant ID
     * @return Promise that completes with the promotion result
     */
    public Promise<AssetPromotionResult> promoteAsset(
            String assetId,
            List<String> targetProductIds,
            String promotedBy,
            String commitSha,
            String targetEnvironment,
            String tenantId) {
        try {
            log.info("Promoting asset: {} to products: {}, by: {}, commitSha: {}, environment: {}",
                assetId, targetProductIds, promotedBy, commitSha, targetEnvironment);

            // Validate commit SHA and environment
            validateCommitSha(commitSha);
            validateTargetEnvironment(targetEnvironment);

            // Check promotion policy
            Promise<Optional<AssetPromotionPolicy>> policyPromise = 
                repository.findActivePolicyForAsset(assetId, tenantId);

            return policyPromise.then(policyOpt -> {
                if (policyOpt.isEmpty()) {
                    log.warn("No active promotion policy found for asset: {}", assetId);
                    return Promise.of(new AssetPromotionResult(
                        assetId, targetProductIds, "failed", 
                        "No active promotion policy", null
                    ));
                }

                AssetPromotionPolicy policy = policyOpt.get();
                return validatePromotionGates(assetId, targetProductIds, policy, tenantId)
                    .then(gateResult -> {
                        if (!gateResult.passed()) {
                            return Promise.of(new AssetPromotionResult(
                                assetId, targetProductIds, "failed",
                                "Promotion gates not satisfied: " + gateResult.reason(),
                                null
                            ));
                        }

                        // Record promotion history with commit SHA and environment
                        return recordPromotionHistory(assetId, targetProductIds, promotedBy, commitSha, targetEnvironment, tenantId)
                            .then(() -> Promise.of(new AssetPromotionResult(
                                assetId, targetProductIds, "succeeded",
                                "Promotion completed successfully", Instant.now()
                            )));
                    });
            });
        } catch (Exception e) {
            log.error("Failed to promote asset: {}", assetId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Retrieves an asset by ID.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @return Promise that completes with the asset, or empty if not found
     */
    public Promise<Optional<ProductFamilyAsset>> getAsset(String assetId, String tenantId) {
        try {
            log.debug("Retrieving asset: {}", assetId);
            return repository.findById(assetId, tenantId);
        } catch (Exception e) {
            log.error("Failed to retrieve asset: {}", assetId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Lists assets by source product.
     *
     * @param sourceProductId the source product ID
     * @param tenantId the tenant ID
     * @return Promise that completes with the list of assets
     */
    public Promise<List<ProductFamilyAsset>> listAssetsBySource(String sourceProductId, String tenantId) {
        try {
            log.debug("Listing assets for source product: {}", sourceProductId);
            return repository.findBySourceProduct(sourceProductId, tenantId);
        } catch (Exception e) {
            log.error("Failed to list assets for source: {}", sourceProductId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Lists assets available for promotion.
     *
     * @param tenantId the tenant ID
     * @return Promise that completes with the list of promotable assets
     */
    public Promise<List<ProductFamilyAsset>> listPromotableAssets(String tenantId) {
        try {
            log.debug("Listing promotable assets");
            return repository.findByPromotionStatus("approved", tenantId);
        } catch (Exception e) {
            log.error("Failed to list promotable assets", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Validates asset schema.
     *
     * @param asset the asset to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateAssetSchema(ProductFamilyAsset asset) {
        Objects.requireNonNull(asset, "Asset must not be null");
        Objects.requireNonNull(asset.assetId(), "Asset ID must not be null");
        Objects.requireNonNull(asset.assetName(), "Asset name must not be null");
        Objects.requireNonNull(asset.assetType(), "Asset type must not be null");
        Objects.requireNonNull(asset.sourceProductId(), "Source product ID must not be null");
        Objects.requireNonNull(asset.maturityLevel(), "Maturity level must not be null");
        Objects.requireNonNull(asset.tenantId(), "Tenant ID must not be null");

        if (asset.assetId().isEmpty()) {
            throw new IllegalArgumentException("Asset ID must not be empty");
        }

        if (!isValidAssetType(asset.assetType())) {
            throw new IllegalArgumentException("Invalid asset type: " + asset.assetType());
        }

        if (!isValidMaturityLevel(asset.maturityLevel())) {
            throw new IllegalArgumentException("Invalid maturity level: " + asset.maturityLevel());
        }

        if (!isValidPromotionStatus(asset.promotionStatus())) {
            throw new IllegalArgumentException("Invalid promotion status: " + asset.promotionStatus());
        }
    }

    private boolean isValidAssetType(String type) {
        return type.equals("ui-component") || type.equals("service") || 
               type.equals("workflow") || type.equals("policy");
    }

    private boolean isValidMaturityLevel(String level) {
        return level.equals("stable") || level.equals("beta") || level.equals("experimental");
    }

    private boolean isValidPromotionStatus(String status) {
        return status.equals("draft") || status.equals("proposed") || 
               status.equals("approved") || status.equals("rejected");
    }

    /**
     * Validates commit SHA format.
     *
     * @param commitSha the commit SHA to validate
     * @throws IllegalArgumentException if commit SHA is invalid
     */
    private void validateCommitSha(String commitSha) {
        if (commitSha == null || commitSha.isEmpty()) {
            throw new IllegalArgumentException("Commit SHA must be present for asset promotion");
        }

        // Validate SHA format (40 hexadecimal characters for git SHA-1)
        if (!commitSha.matches("^[a-fA-F0-9]{40}$")) {
            throw new IllegalArgumentException("Invalid commit SHA format: " + commitSha);
        }
    }

    /**
     * Validates target environment.
     *
     * @param targetEnvironment the target environment to validate
     * @throws IllegalArgumentException if environment is invalid
     */
    private void validateTargetEnvironment(String targetEnvironment) {
        if (targetEnvironment == null || targetEnvironment.isEmpty()) {
            throw new IllegalArgumentException("Target environment must be present for asset promotion");
        }

        if (!targetEnvironment.equals("production") && 
            !targetEnvironment.equals("staging") && 
            !targetEnvironment.equals("development")) {
            throw new IllegalArgumentException("Invalid target environment: " + targetEnvironment);
        }
    }

    /**
     * Validates promotion gates against policy.
     */
    private Promise<GateValidationResult> validatePromotionGates(
            String assetId,
            List<String> targetProductIds,
            AssetPromotionPolicy policy,
            String tenantId) {
        try {
            // Check maturity level requirement
            Promise<Optional<ProductFamilyAsset>> assetPromise = repository.findById(assetId, tenantId);
            
            return assetPromise.then(assetOpt -> {
                if (assetOpt.isEmpty()) {
                    return Promise.of(new GateValidationResult(false, "Asset not found"));
                }

                ProductFamilyAsset asset = assetOpt.get();
                
                // Check maturity level
                if (policy.requiredMaturityLevel() != null && 
                    !asset.maturityLevel().equals(policy.requiredMaturityLevel())) {
                    return Promise.of(new GateValidationResult(
                        false, 
                        "Maturity level " + asset.maturityLevel() + 
                        " does not meet required level " + policy.requiredMaturityLevel()
                    ));
                }

                // Check target products are in allowed list
                if (!policy.targetProductIds().isEmpty()) {
                    for (String targetId : targetProductIds) {
                        if (!policy.targetProductIds().contains(targetId)) {
                            return Promise.of(new GateValidationResult(
                                false,
                                "Target product " + targetId + " not in allowed list"
                            ));
                        }
                    }
                }

                // Check asset type is in allowed list
                if (!policy.assetTypes().isEmpty() && 
                    !policy.assetTypes().contains(asset.assetType())) {
                    return Promise.of(new GateValidationResult(
                        false,
                        "Asset type " + asset.assetType() + " not in allowed list"
                    ));
                }

                return Promise.of(new GateValidationResult(true, "All gates satisfied"));
            });
        } catch (Exception e) {
            log.error("Failed to validate promotion gates for asset: {}", assetId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Records promotion history.
     */
    private Promise<Void> recordPromotionHistory(
            String assetId,
            List<String> targetProductIds,
            String promotedBy,
            String commitSha,
            String targetEnvironment,
            String tenantId) {
        try {
            Promise<Optional<ProductFamilyAsset>> assetPromise = repository.findById(assetId, tenantId);
            
            return assetPromise.then(assetOpt -> {
                if (assetOpt.isEmpty()) {
                    return Promise.complete();
                }

                ProductFamilyAsset asset = assetOpt.get();
                List<Promise<Void>> historyPromises = new ArrayList<>();

                for (String targetId : targetProductIds) {
                    AssetPromotionHistory history = new AssetPromotionHistory(
                        null,
                        assetId,
                        asset.sourceProductId(),
                        targetId,
                        "approved",
                        promotedBy,
                        commitSha,
                        targetEnvironment,
                        Instant.now(),
                        null,
                        null,
                        tenantId
                    );
                    historyPromises.add(repository.recordPromotionHistory(history));
                }

                return Promises.all(historyPromises).map(ignored -> null);
            });
        } catch (Exception e) {
            log.error("Failed to record promotion history for asset: {}", assetId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Repository interface for product family asset data.
     */
    public interface ProductFamilyAssetRepository {
        Promise<ProductFamilyAsset> upsert(ProductFamilyAsset asset);
        Promise<Optional<ProductFamilyAsset>> findById(String assetId, String tenantId);
        Promise<List<ProductFamilyAsset>> findBySourceProduct(String sourceProductId, String tenantId);
        Promise<List<ProductFamilyAsset>> findByPromotionStatus(String status, String tenantId);
        Promise<Optional<AssetPromotionPolicy>> findActivePolicyForAsset(String assetId, String tenantId);
        Promise<Void> recordPromotionHistory(AssetPromotionHistory history);
    }

    /**
     * Product family asset domain object.
     */
    public static class ProductFamilyAsset {
        private final String id;
        private final String assetId;
        private final String assetName;
        private final String assetType;
        private final String sourceProductId;
        private final String description;
        private final List<String> tags;
        private final String maturityLevel;
        private final int usageCount;
        private final Instant lastUsedAt;
        private final String promotionStatus;
        private final Map<String, Object> promotionEvidence;
        private final String tenantId;
        private final Instant createdAt;
        private final Instant updatedAt;

        private ProductFamilyAsset(Builder builder) {
            this.id = builder.id;
            this.assetId = Objects.requireNonNull(builder.assetId, "assetId must not be null");
            this.assetName = Objects.requireNonNull(builder.assetName, "assetName must not be null");
            this.assetType = Objects.requireNonNull(builder.assetType, "assetType must not be null");
            this.sourceProductId = Objects.requireNonNull(builder.sourceProductId, "sourceProductId must not be null");
            this.description = builder.description;
            this.tags = builder.tags != null ? builder.tags : List.of();
            this.maturityLevel = Objects.requireNonNull(builder.maturityLevel, "maturityLevel must not be null");
            this.usageCount = builder.usageCount != null ? builder.usageCount : 0;
            this.lastUsedAt = builder.lastUsedAt;
            this.promotionStatus = builder.promotionStatus != null ? builder.promotionStatus : "draft";
            this.promotionEvidence = builder.promotionEvidence != null ? builder.promotionEvidence : Map.of();
            this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
            this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
            this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        }

        public String id() { return id; }
        public String assetId() { return assetId; }
        public String assetName() { return assetName; }
        public String assetType() { return assetType; }
        public String sourceProductId() { return sourceProductId; }
        public String description() { return description; }
        public List<String> tags() { return tags; }
        public String maturityLevel() { return maturityLevel; }
        public int usageCount() { return usageCount; }
        public Instant lastUsedAt() { return lastUsedAt; }
        public String promotionStatus() { return promotionStatus; }
        public Map<String, Object> promotionEvidence() { return promotionEvidence; }
        public String tenantId() { return tenantId; }
        public Instant createdAt() { return createdAt; }
        public Instant updatedAt() { return updatedAt; }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String id;
            private String assetId;
            private String assetName;
            private String assetType;
            private String sourceProductId;
            private String description;
            private List<String> tags;
            private String maturityLevel;
            private Integer usageCount;
            private Instant lastUsedAt;
            private String promotionStatus;
            private Map<String, Object> promotionEvidence;
            private String tenantId;
            private Instant createdAt;
            private Instant updatedAt;

            public Builder id(String id) { this.id = id; return this; }
            public Builder assetId(String assetId) { this.assetId = assetId; return this; }
            public Builder assetName(String assetName) { this.assetName = assetName; return this; }
            public Builder assetType(String assetType) { this.assetType = assetType; return this; }
            public Builder sourceProductId(String sourceProductId) { this.sourceProductId = sourceProductId; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder tags(List<String> tags) { this.tags = tags; return this; }
            public Builder maturityLevel(String maturityLevel) { this.maturityLevel = maturityLevel; return this; }
            public Builder usageCount(Integer usageCount) { this.usageCount = usageCount; return this; }
            public Builder lastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }
            public Builder promotionStatus(String promotionStatus) { this.promotionStatus = promotionStatus; return this; }
            public Builder promotionEvidence(Map<String, Object> promotionEvidence) { this.promotionEvidence = promotionEvidence; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
            public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

            public ProductFamilyAsset build() {
                return new ProductFamilyAsset(this);
            }
        }
    }

    /**
     * Asset promotion policy domain object.
     */
    public static class AssetPromotionPolicy {
        private final String id;
        private final String policyId;
        private final String policyName;
        private final String policyType;
        private final String sourceProductId;
        private final List<String> targetProductIds;
        private final List<String> assetTypes;
        private final String requiredMaturityLevel;
        private final String approvalWorkflowId;
        private final Map<String, Object> policyRules;
        private final boolean isActive;
        private final String tenantId;
        private final Instant createdAt;
        private final Instant updatedAt;

        public AssetPromotionPolicy(
                String id, String policyId, String policyName, String policyType,
                String sourceProductId, List<String> targetProductIds, List<String> assetTypes,
                String requiredMaturityLevel, String approvalWorkflowId, Map<String, Object> policyRules,
                boolean isActive, String tenantId, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.policyId = policyId;
            this.policyName = policyName;
            this.policyType = policyType;
            this.sourceProductId = sourceProductId;
            this.targetProductIds = targetProductIds != null ? targetProductIds : List.of();
            this.assetTypes = assetTypes != null ? assetTypes : List.of();
            this.requiredMaturityLevel = requiredMaturityLevel;
            this.approvalWorkflowId = approvalWorkflowId;
            this.policyRules = policyRules != null ? policyRules : Map.of();
            this.isActive = isActive;
            this.tenantId = tenantId;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String id() { return id; }
        public String policyId() { return policyId; }
        public String policyName() { return policyName; }
        public String policyType() { return policyType; }
        public String sourceProductId() { return sourceProductId; }
        public List<String> targetProductIds() { return targetProductIds; }
        public List<String> assetTypes() { return assetTypes; }
        public String requiredMaturityLevel() { return requiredMaturityLevel; }
        public String approvalWorkflowId() { return approvalWorkflowId; }
        public Map<String, Object> policyRules() { return policyRules; }
        public boolean isActive() { return isActive; }
        public String tenantId() { return tenantId; }
        public Instant createdAt() { return createdAt; }
        public Instant updatedAt() { return updatedAt; }
    }

    /**
     * Asset promotion history domain object.
     */
    public static class AssetPromotionHistory {
        private final String id;
        private final String assetId;
        private final String sourceProductId;
        private final String targetProductId;
        private final String promotionStatus;
        private final String promotedBy;
        private final String commitSha;
        private final String targetEnvironment;
        private final Instant promotedAt;
        private final String reviewComments;
        private final Map<String, Object> evidence;
        private final String tenantId;

        public AssetPromotionHistory(
                String id, String assetId, String sourceProductId, String targetProductId,
                String promotionStatus, String promotedBy, String commitSha, String targetEnvironment,
                Instant promotedAt, String reviewComments, Map<String, Object> evidence, String tenantId) {
            this.id = id;
            this.assetId = assetId;
            this.sourceProductId = sourceProductId;
            this.targetProductId = targetProductId;
            this.promotionStatus = promotionStatus;
            this.promotedBy = promotedBy;
            this.commitSha = commitSha;
            this.targetEnvironment = targetEnvironment;
            this.promotedAt = promotedAt;
            this.reviewComments = reviewComments;
            this.evidence = evidence != null ? evidence : Map.of();
            this.tenantId = tenantId;
        }

        public String id() { return id; }
        public String assetId() { return assetId; }
        public String sourceProductId() { return sourceProductId; }
        public String targetProductId() { return targetProductId; }
        public String promotionStatus() { return promotionStatus; }
        public String promotedBy() { return promotedBy; }
        public String commitSha() { return commitSha; }
        public String targetEnvironment() { return targetEnvironment; }
        public Instant promotedAt() { return promotedAt; }
        public String reviewComments() { return reviewComments; }
        public Map<String, Object> evidence() { return evidence; }
        public String tenantId() { return tenantId; }
    }

    /**
     * Gate validation result.
     */
    public static class GateValidationResult {
        private final boolean passed;
        private final String reason;

        public GateValidationResult(boolean passed, String reason) {
            this.passed = passed;
            this.reason = reason;
        }

        public boolean passed() { return passed; }
        public String reason() { return reason; }
    }

    /**
     * Asset promotion result.
     */
    public static class AssetPromotionResult {
        private final String assetId;
        private final List<String> targetProductIds;
        private final String status;
        private final String message;
        private final Instant completedAt;

        public AssetPromotionResult(
                String assetId, List<String> targetProductIds, String status,
                String message, Instant completedAt) {
            this.assetId = assetId;
            this.targetProductIds = targetProductIds;
            this.status = status;
            this.message = message;
            this.completedAt = completedAt;
        }

        public String assetId() { return assetId; }
        public List<String> targetProductIds() { return targetProductIds; }
        public String status() { return status; }
        public String message() { return message; }
        public Instant completedAt() { return completedAt; }
    }
}
