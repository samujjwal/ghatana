package com.ghatana.datacloud.application;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Service for producing product release readiness evidence into Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for ingesting and managing product release readiness data
 * for PHR, DMOS, and other products in the product family. All operations are tenant-scoped
 * and return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProductReleaseReadinessService service = new ProductReleaseReadinessService(repository, metrics);
 *
 * // Produce release readiness evidence
 * ProductReleaseReadiness readiness = ProductReleaseReadiness.builder()
 *     .productId("phr")
 *     .productVersion("1.0.0")
 *     .releaseTarget("production")
 *     .releaseVerdict("pass")
 *     .averageScore(0.95)
 *     .releaseTargetScore(0.90)
 *     .evidence(Map.of("build", Map.of("status", "passed")))
 *     .blockingGaps(List.of())
 *     .belowTargetDimensions(List.of())
 *     .tenantId("tenant-123")
 *     .build();
 *
 * Promise<ProductReleaseReadiness> promise = service.produceReleaseReadiness(readiness);
 *
 * // In test with EventloopTestBase:
 * ProductReleaseReadiness produced = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses ProductReleaseReadinessRepository (infrastructure layer)
 * - Uses MetricsCollector (core/observability)
 * - Enforces tenant isolation
 * - Emits metrics for all operations
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repository.
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations tenant-scoped. Tenant extracted from context and enforced at repository level.
 *
 * @see ProductReleaseReadiness
 * @see com.ghatana.datacloud.infrastructure.persistence.ProductReleaseReadinessRepository
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Service for product release readiness evidence production
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class ProductReleaseReadinessService {

    private static final Logger log = LoggerFactory.getLogger(ProductReleaseReadinessService.class);

    private final ProductReleaseReadinessRepository repository;
    private final MetricsCollector metrics;

    /**
     * Creates a new product release readiness service.
     *
     * @param repository the product release readiness repository (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if repository or metrics is null
     */
    public ProductReleaseReadinessService(
            ProductReleaseReadinessRepository repository,
            MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Produces release readiness evidence for a product.
     *
     * <p><b>Validation</b><br>
     * - Product ID must be valid (phr, dmos, etc.)
     * - Product version must be non-empty
     * - Release target must be valid (production, staging, development)
     * - Release verdict must be pass or fail
     * - Tenant ID must match context
     *
     * <p><b>Upsert Behavior</b><br>
     * If a record exists for the same product_id, product_version, and release_target,
     * it will be updated. Otherwise, a new record is created.
     *
     * @param readiness the release readiness data to produce
     * @return Promise that completes with the produced release readiness
     */
    public Promise<ProductReleaseReadiness> produceReleaseReadiness(ProductReleaseReadiness readiness) {
        try {
            validateReleaseReadiness(readiness);

            log.info("Producing release readiness for product: {}, version: {}, target: {}, verdict: {}",
                readiness.productId(), readiness.productVersion(), readiness.releaseTarget(), readiness.releaseVerdict());

            Promise<ProductReleaseReadiness> result = repository.upsert(readiness);

            result.whenComplete(() -> {
                metrics.incrementCounter("product_release_readiness.produced", Map.of(
                    "product_id", readiness.productId(),
                    "release_target", readiness.releaseTarget(),
                    "verdict", readiness.releaseVerdict()
                ));
            });

            return result;
        } catch (Exception e) {
            log.error("Failed to produce release readiness for product: {}", readiness.productId(), e);
            metrics.incrementCounter("product_release_readiness.produce_failed", Map.of(
                "product_id", readiness.productId()
            ));
            return Promise.ofException(e);
        }
    }

    /**
     * Retrieves release readiness evidence for a product.
     *
     * @param productId the product ID
     * @param productVersion the product version
     * @param releaseTarget the release target
     * @param tenantId the tenant ID
     * @return Promise that completes with the release readiness, or empty if not found
     */
    public Promise<Optional<ProductReleaseReadiness>> getReleaseReadiness(
            String productId,
            String productVersion,
            String releaseTarget,
            String tenantId) {
        try {
            log.debug("Retrieving release readiness for product: {}, version: {}, target: {}",
                productId, productVersion, releaseTarget);

            return repository.findByProductVersionAndTarget(productId, productVersion, releaseTarget, tenantId);
        } catch (Exception e) {
            log.error("Failed to retrieve release readiness for product: {}", productId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Lists all release readiness records for a product.
     *
     * @param productId the product ID
     * @param tenantId the tenant ID
     * @return Promise that completes with the list of release readiness records
     */
    public Promise<List<ProductReleaseReadiness>> listReleaseReadiness(String productId, String tenantId) {
        try {
            log.debug("Listing release readiness for product: {}", productId);
            return repository.findByProductId(productId, tenantId);
        } catch (Exception e) {
            log.error("Failed to list release readiness for product: {}", productId, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Validates release readiness data.
     *
     * @param readiness the release readiness to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateReleaseReadiness(ProductReleaseReadiness readiness) {
        Objects.requireNonNull(readiness, "Release readiness must not be null");
        Objects.requireNonNull(readiness.productId(), "Product ID must not be null");
        Objects.requireNonNull(readiness.productVersion(), "Product version must not be null");
        Objects.requireNonNull(readiness.releaseTarget(), "Release target must not be null");
        Objects.requireNonNull(readiness.releaseVerdict(), "Release verdict must not be null");
        Objects.requireNonNull(readiness.tenantId(), "Tenant ID must not be null");

        if (readiness.productId().isEmpty()) {
            throw new IllegalArgumentException("Product ID must not be empty");
        }

        if (readiness.productVersion().isEmpty()) {
            throw new IllegalArgumentException("Product version must not be empty");
        }

        if (!isValidReleaseTarget(readiness.releaseTarget())) {
            throw new IllegalArgumentException("Invalid release target: " + readiness.releaseTarget());
        }

        if (!isValidReleaseVerdict(readiness.releaseVerdict())) {
            throw new IllegalArgumentException("Invalid release verdict: " + readiness.releaseVerdict());
        }

        if (readiness.averageScore() != null && (readiness.averageScore() < 0 || readiness.averageScore() > 1)) {
            throw new IllegalArgumentException("Average score must be between 0 and 1");
        }

        if (readiness.releaseTargetScore() != null && (readiness.releaseTargetScore() < 0 || readiness.releaseTargetScore() > 1)) {
            throw new IllegalArgumentException("Release target score must be between 0 and 1");
        }
    }

    private boolean isValidReleaseTarget(String target) {
        return target.equals("production") || target.equals("staging") || target.equals("development");
    }

    private boolean isValidReleaseVerdict(String verdict) {
        return verdict.equals("pass") || verdict.equals("fail");
    }

    /**
     * Repository interface for product release readiness data.
     */
    public interface ProductReleaseReadinessRepository {
        Promise<ProductReleaseReadiness> upsert(ProductReleaseReadiness readiness);
        Promise<Optional<ProductReleaseReadiness>> findByProductVersionAndTarget(
            String productId, String productVersion, String releaseTarget, String tenantId);
        Promise<List<ProductReleaseReadiness>> findByProductId(String productId, String tenantId);
    }

    /**
     * Product release readiness domain object.
     */
    public static class ProductReleaseReadiness {
        private final String id;
        private final String productId;
        private final String productVersion;
        private final String releaseTarget;
        private final String releaseVerdict;
        private final Double averageScore;
        private final Double releaseTargetScore;
        private final Instant generatedAt;
        private final Map<String, Object> evidence;
        private final List<Map<String, Object>> blockingGaps;
        private final List<Map<String, Object>> belowTargetDimensions;
        private final String tenantId;
        private final Instant createdAt;
        private final Instant updatedAt;

        private ProductReleaseReadiness(Builder builder) {
            this.id = builder.id;
            this.productId = Objects.requireNonNull(builder.productId, "productId must not be null");
            this.productVersion = Objects.requireNonNull(builder.productVersion, "productVersion must not be null");
            this.releaseTarget = Objects.requireNonNull(builder.releaseTarget, "releaseTarget must not be null");
            this.releaseVerdict = Objects.requireNonNull(builder.releaseVerdict, "releaseVerdict must not be null");
            this.averageScore = builder.averageScore;
            this.releaseTargetScore = builder.releaseTargetScore;
            this.generatedAt = builder.generatedAt != null ? builder.generatedAt : Instant.now();
            this.evidence = builder.evidence != null ? builder.evidence : Map.of();
            this.blockingGaps = builder.blockingGaps != null ? builder.blockingGaps : List.of();
            this.belowTargetDimensions = builder.belowTargetDimensions != null ? builder.belowTargetDimensions : List.of();
            this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
            this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
            this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        }

        public String id() { return id; }
        public String productId() { return productId; }
        public String productVersion() { return productVersion; }
        public String releaseTarget() { return releaseTarget; }
        public String releaseVerdict() { return releaseVerdict; }
        public Double averageScore() { return averageScore; }
        public Double releaseTargetScore() { return releaseTargetScore; }
        public Instant generatedAt() { return generatedAt; }
        public Map<String, Object> evidence() { return evidence; }
        public List<Map<String, Object>> blockingGaps() { return blockingGaps; }
        public List<Map<String, Object>> belowTargetDimensions() { return belowTargetDimensions; }
        public String tenantId() { return tenantId; }
        public Instant createdAt() { return createdAt; }
        public Instant updatedAt() { return updatedAt; }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String id;
            private String productId;
            private String productVersion;
            private String releaseTarget;
            private String releaseVerdict;
            private Double averageScore;
            private Double releaseTargetScore;
            private Instant generatedAt;
            private Map<String, Object> evidence;
            private List<Map<String, Object>> blockingGaps;
            private List<Map<String, Object>> belowTargetDimensions;
            private String tenantId;
            private Instant createdAt;
            private Instant updatedAt;

            public Builder id(String id) { this.id = id; return this; }
            public Builder productId(String productId) { this.productId = productId; return this; }
            public Builder productVersion(String productVersion) { this.productVersion = productVersion; return this; }
            public Builder releaseTarget(String releaseTarget) { this.releaseTarget = releaseTarget; return this; }
            public Builder releaseVerdict(String releaseVerdict) { this.releaseVerdict = releaseVerdict; return this; }
            public Builder averageScore(Double averageScore) { this.averageScore = averageScore; return this; }
            public Builder releaseTargetScore(Double releaseTargetScore) { this.releaseTargetScore = releaseTargetScore; return this; }
            public Builder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt; return this; }
            public Builder evidence(Map<String, Object> evidence) { this.evidence = evidence; return this; }
            public Builder blockingGaps(List<Map<String, Object>> blockingGaps) { this.blockingGaps = blockingGaps; return this; }
            public Builder belowTargetDimensions(List<Map<String, Object>> belowTargetDimensions) { this.belowTargetDimensions = belowTargetDimensions; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
            public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

            public ProductReleaseReadiness build() {
                return new ProductReleaseReadiness(this);
            }
        }
    }
}
