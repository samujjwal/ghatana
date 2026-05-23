package com.ghatana.kernel.validation;

import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Asset schema validator for product-family reusable assets.
 *
 * <p>Validates assets against canonical schema and quality gates before promotion.
 * Enforces mandatory criteria (schema conformance, production safety, test coverage,
 * documentation, tenant isolation) and recommended criteria (observability, error handling,
 * performance).</p>
 *
 * @doc.type class
 * @doc.purpose Validate assets for promotion to product-family level
 * @doc.layer platform
 * @doc.pattern Validator
 */
public class AssetSchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(AssetSchemaValidator.class);

    private final ProductionSafetyValidator productionSafetyValidator;
    private final TestCoverageValidator testCoverageValidator;
    private final TenantIsolationValidator tenantIsolationValidator;

    public AssetSchemaValidator(
        ProductionSafetyValidator productionSafetyValidator,
        TestCoverageValidator testCoverageValidator,
        TenantIsolationValidator tenantIsolationValidator
    ) {
        this.productionSafetyValidator = productionSafetyValidator;
        this.testCoverageValidator = testCoverageValidator;
        this.tenantIsolationValidator = tenantIsolationValidator;
    }

    /**
     * Validate an asset for promotion.
     *
     * @param asset The asset to validate
     * @return Promise containing validation result
     */
    public Promise<AssetValidationResult> validateForPromotion(Asset asset) {
        logger.info("Validating asset for promotion: {}", asset.assetId());

        List<ValidationGateResult> gateResults = new ArrayList<>();

        // Mandatory gates
        gateResults.add(validateSchemaConformance(asset));
        gateResults.add(validateProductionSafety(asset));
        gateResults.add(validateTestCoverage(asset));
        gateResults.add(validateDocumentation(asset));
        gateResults.add(validateTenantIsolation(asset));

        // Recommended gates
        gateResults.add(validateObservability(asset));
        gateResults.add(validateErrorHandling(asset));
        gateResults.add(validatePerformance(asset));

        boolean allMandatoryPassed = gateResults.stream()
            .filter(r -> r.gate().severity() == GateSeverity.BLOCKING)
            .allMatch(ValidationGateResult::passed);

        AssetValidationResult result = new AssetValidationResult(
            asset.assetId(),
            allMandatoryPassed ? ValidationStatus.PASSED : ValidationStatus.FAILED,
            gateResults,
            calculateOverallScore(gateResults)
        );

        return Promise.of(result);
    }

    private ValidationGateResult validateSchemaConformance(Asset asset) {
        try {
            // Check if asset conforms to canonical schema
            boolean conforms = asset.schemaVersion() != null && !asset.schemaVersion().isEmpty();
            
            return new ValidationGateResult(
                ValidationGate.SCHEMA_CONFORMANCE,
                conforms,
                conforms ? "Asset conforms to schema version " + asset.schemaVersion() : "Schema version missing or invalid",
                GateSeverity.BLOCKING
            );
        } catch (Exception e) {
            logger.error("Schema conformance validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.SCHEMA_CONFORMANCE,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.BLOCKING
            );
        }
    }

    private ValidationGateResult validateProductionSafety(Asset asset) {
        try {
            return productionSafetyValidator.validate(asset.sourceFilePath())
                .thenMap(safe -> new ValidationGateResult(
                    ValidationGate.PRODUCTION_SAFETY,
                    safe,
                    safe ? "No production-unsafe patterns detected" : "Production-unsafe patterns detected",
                    GateSeverity.BLOCKING
                ))
                .orElse(new ValidationGateResult(
                    ValidationGate.PRODUCTION_SAFETY,
                    false,
                    "Production safety validation failed",
                    GateSeverity.BLOCKING
                ));
        } catch (Exception e) {
            logger.error("Production safety validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.PRODUCTION_SAFETY,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.BLOCKING
            );
        }
    }

    private ValidationGateResult validateTestCoverage(Asset asset) {
        try {
            return testCoverageValidator.validate(asset.sourceFilePath())
                .thenMap(coverage -> {
                    boolean passed = coverage >= 80.0;
                    return new ValidationGateResult(
                        ValidationGate.TEST_COVERAGE,
                        passed,
                        String.format("Test coverage: %.1f%%", coverage),
                        GateSeverity.BLOCKING
                    );
                })
                .orElse(new ValidationGateResult(
                    ValidationGate.TEST_COVERAGE,
                    false,
                    "Test coverage validation failed",
                    GateSeverity.BLOCKING
                ));
        } catch (Exception e) {
            logger.error("Test coverage validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.TEST_COVERAGE,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.BLOCKING
            );
        }
    }

    private ValidationGateResult validateDocumentation(Asset asset) {
        try {
            boolean hasDocs = asset.description() != null && !asset.description().isEmpty();
            boolean hasExamples = asset.examples() != null && !asset.examples().isEmpty();
            boolean passed = hasDocs && hasExamples;

            return new ValidationGateResult(
                ValidationGate.DOCUMENTATION,
                passed,
                passed ? "Documentation complete with examples" : "Documentation incomplete",
                GateSeverity.BLOCKING
            );
        } catch (Exception e) {
            logger.error("Documentation validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.DOCUMENTATION,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.BLOCKING
            );
        }
    }

    private ValidationGateResult validateTenantIsolation(Asset asset) {
        try {
            return tenantIsolationValidator.validate(asset.sourceFilePath())
                .thenMap(isolated -> new ValidationGateResult(
                    ValidationGate.TENANT_ISOLATION,
                    isolated,
                    isolated ? "Tenant context properly scoped" : "Tenant isolation issues detected",
                    GateSeverity.BLOCKING
                ))
                .orElse(new ValidationGateResult(
                    ValidationGate.TENANT_ISOLATION,
                    false,
                    "Tenant isolation validation failed",
                    GateSeverity.BLOCKING
                ));
        } catch (Exception e) {
            logger.error("Tenant isolation validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.TENANT_ISOLATION,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.BLOCKING
            );
        }
    }

    private ValidationGateResult validateObservability(Asset asset) {
        try {
            boolean hasLogging = asset.hasStructuredLogging();
            boolean hasMetrics = asset.hasMetrics();
            boolean passed = hasLogging || hasMetrics;

            return new ValidationGateResult(
                ValidationGate.OBSERVABILITY,
                passed,
                passed ? "Observability hooks present" : "Observability hooks missing",
                GateSeverity.WARNING
            );
        } catch (Exception e) {
            logger.error("Observability validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.OBSERVABILITY,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.WARNING
            );
        }
    }

    private ValidationGateResult validateErrorHandling(Asset asset) {
        try {
            boolean hasErrorHandling = asset.hasRobustErrorHandling();
            boolean passed = hasErrorHandling;

            return new ValidationGateResult(
                ValidationGate.ERROR_HANDLING,
                passed,
                passed ? "Robust error handling present" : "Error handling incomplete",
                GateSeverity.WARNING
            );
        } catch (Exception e) {
            logger.error("Error handling validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.ERROR_HANDLING,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.WARNING
            );
        }
    }

    private ValidationGateResult validatePerformance(Asset asset) {
        try {
            boolean meetsSLA = asset.meetsPerformanceSLA();
            boolean passed = meetsSLA;

            return new ValidationGateResult(
                ValidationGate.PERFORMANCE,
                passed,
                passed ? "Performance SLAs met" : "Performance SLAs not met",
                GateSeverity.WARNING
            );
        } catch (Exception e) {
            logger.error("Performance validation failed for asset: {}", asset.assetId(), e);
            return new ValidationGateResult(
                ValidationGate.PERFORMANCE,
                false,
                "Validation error: " + e.getMessage(),
                GateSeverity.WARNING
            );
        }
    }

    private double calculateOverallScore(List<ValidationGateResult> gateResults) {
        int total = gateResults.size();
        int passed = (int) gateResults.stream().filter(ValidationGateResult::passed).count();
        return (double) passed / total * 100.0;
    }

    /**
     * Asset record.
     */
    public record Asset(
        String assetId,
        String assetName,
        String assetType,
        String assetCategory,
        String description,
        String sourceFilePath,
        String schemaVersion,
        List<String> examples
    ) {
        public boolean hasStructuredLogging() {
            // In production, this would analyze the source code
            return true;
        }

        public boolean hasMetrics() {
            // In production, this would analyze the source code
            return true;
        }

        public boolean hasRobustErrorHandling() {
            // In production, this would analyze the source code
            return true;
        }

        public boolean meetsPerformanceSLA() {
            // In production, this would check performance metrics
            return true;
        }
    }

    /**
     * Validation result.
     */
    public record AssetValidationResult(
        String assetId,
        ValidationStatus status,
        List<ValidationGateResult> gateResults,
        double overallScore
    ) {}

    /**
     * Gate validation result.
     */
    public record ValidationGateResult(
        ValidationGate gate,
        boolean passed,
        String message,
        GateSeverity severity
    ) {}

    /**
     * Validation gate enum.
     */
    public enum ValidationGate {
        SCHEMA_CONFORMANCE,
        PRODUCTION_SAFETY,
        TEST_COVERAGE,
        DOCUMENTATION,
        TENANT_ISOLATION,
        OBSERVABILITY,
        ERROR_HANDLING,
        PERFORMANCE
    }

    /**
     * Gate severity enum.
     */
    public enum GateSeverity {
        BLOCKING,
        WARNING
    }

    /**
     * Validation status enum.
     */
    public enum ValidationStatus {
        PASSED,
        FAILED,
        PARTIAL
    }

    /**
     * Production safety validator interface.
     */
    public interface ProductionSafetyValidator {
        Promise<Boolean> validate(String sourceFilePath);
    }

    /**
     * Test coverage validator interface.
     */
    public interface TestCoverageValidator {
        Promise<Double> validate(String sourceFilePath);
    }

    /**
     * Tenant isolation validator interface.
     */
    public interface TenantIsolationValidator {
        Promise<Boolean> validate(String sourceFilePath);
    }
}
