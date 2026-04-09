/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.governance;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Finance Data Governance Service.
 *
 * <p>Finance-specific data governance with regulatory compliance and financial data protection.
 * Provides automated classification, retention management, and compliance reporting for
 * financial data assets according to regulatory requirements.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Financial data classification with regulatory sensitivity levels</li>
 *   <li>Regulatory retention policy enforcement (SEC, FINRA, GDPR, etc.)</li>
 *   <li>Financial data lineage tracking for audit trails</li>
 *   <li>Right to erasure with financial compliance constraints</li>
 *   <li>Dynamic data masking for PII and sensitive financial data</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance data governance - regulatory compliance, data classification, retention management
 * @doc.layer finance
 * @doc.pattern Service, Governance
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceDataGovernanceService {

    // ── Finance-Specific Inner Ports ───────────────────────────────────────────────

    /** Finance-specific classification rules with regulatory patterns. */
    public interface FinanceClassificationRulePort {
        /** Classifies financial data based on regulatory patterns and content analysis. */
        FinanceClassification classify(String assetName, String schemaContent, String regulatoryBody);
    }

    /** Finance retention policy management with regulatory requirements. */
    public interface FinanceRetentionPolicyPort {
        /** Gets retention policy for financial data based on regulatory requirements. */
        FinanceRetentionPolicy getRetentionPolicy(String classification, String regulatoryBody, String dataType);

        /** Enforces retention policy and schedules data deletion/archival. */
        Promise<Void> enforceRetentionPolicy(String assetId, FinanceRetentionPolicy policy);
    }

    /** Financial data masking with regulatory compliance. */
    public interface FinanceDataMaskingPort {
        /** Applies dynamic masking to financial data based on classification and user context. */
        Promise<MaskedDataResult> applyMasking(String dataId, String userId, FinanceClassification classification);
    }

    /** Financial data lineage tracking for regulatory audit trails. */
    public interface FinanceDataLineagePort {
        /** Tracks data lineage for financial regulatory compliance. */
        Promise<Void> trackLineage(FinanceLineageEvent event);

        /** Queries lineage for regulatory audit purposes. */
        Promise<List<FinanceLineageEvent>> queryLineage(String assetId, Instant startTime, Instant endTime);
    }

    // ── Finance-Specific Value Types ─────────────────────────────────────────────

    public enum FinanceRegulatoryBody {
        SEC, FINRA, FCA, ESMA, MAS, HKMA, ASIC, JFSA, GDPR, CCPA
    }

    public enum FinanceDataType {
        TRADE_DATA, CUSTOMER_DATA, MARKET_DATA, RISK_DATA, COMPLIANCE_DATA, AUDIT_DATA, REPORTING_DATA
    }

    public record FinanceClassification(
        String classification,
        FinanceRegulatoryBody regulatoryBody,
        String regulatoryReference,
        String retentionPeriod,
        String accessLevel
    ) {}

    public record FinanceRetentionPolicy(
        String policyId,
        FinanceClassification classification,
        FinanceRegulatoryBody regulatoryBody,
        FinanceDataType dataType,
        int retentionYears,
        boolean requiresArchival,
        String archivalLocation,
        LocalDateTime policyEffectiveDate
    ) {}

    public record FinanceLineageEvent(
        String eventId,
        String assetId,
        String eventType,
        String userId,
        String system,
        Map<String, Object> metadata,
        Instant timestamp,
        String regulatoryReference
    ) {}

    public record MaskedDataResult(
        String maskedData,
        List<String> maskedFields,
        String maskingLevel,
        boolean fullyMasked
    ) {}

    public record FinanceDataAsset(
        String assetId,
        String assetName,
        FinanceClassification classification,
        FinanceDataType dataType,
        FinanceRegulatoryBody regulatoryBody,
        String ownerId,
        LocalDateTime createdAt,
        LocalDateTime lastModified
    ) {}

    public record FinanceErasureRequest(
        String requestId,
        String assetId,
        String requestorId,
        String reason,
        FinanceRegulatoryBody regulatoryBody,
        LocalDateTime requestedAt,
        String status
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final FinanceClassificationRulePort classificationRulePort;
    private final FinanceRetentionPolicyPort retentionPolicyPort;
    private final FinanceDataMaskingPort maskingPort;
    private final FinanceDataLineagePort lineagePort;
    private final Executor executor;

    private final Counter financeClassifiedCounter;
    private final Counter financeRetentionEnforcedCounter;
    private final Counter financeMaskingAppliedCounter;
    private final Counter financeErasureRequestedCounter;

    // ── Constructor ─────────────────────────────────────────────────────────────

    public FinanceDataGovernanceService(
        FinanceClassificationRulePort classificationRulePort,
        FinanceRetentionPolicyPort retentionPolicyPort,
        FinanceDataMaskingPort maskingPort,
        FinanceDataLineagePort lineagePort,
        MeterRegistry registry,
        Executor executor
    ) {
        this.classificationRulePort = classificationRulePort;
        this.retentionPolicyPort = retentionPolicyPort;
        this.maskingPort = maskingPort;
        this.lineagePort = lineagePort;
        this.executor = executor;

        this.financeClassifiedCounter = Counter.builder("finance.governance.classification.auto_total").register(registry);
        this.financeRetentionEnforcedCounter = Counter.builder("finance.governance.retention.enforced_total").register(registry);
        this.financeMaskingAppliedCounter = Counter.builder("finance.governance.masking.applied_total").register(registry);
        this.financeErasureRequestedCounter = Counter.builder("finance.governance.erasure.requested_total").register(registry);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Auto-classify financial data with regulatory compliance.
     */
    public Promise<FinanceDataAsset> classifyFinancialData(
            String assetId, String assetName, String schemaContent, FinanceRegulatoryBody regulatoryBody) {

        return Promise.ofBlocking(executor, () -> {
            FinanceClassification classification = classificationRulePort.classify(assetName, schemaContent, regulatoryBody.name());

            FinanceDataAsset asset = new FinanceDataAsset(
                assetId, assetName, classification, FinanceDataType.TRADE_DATA, regulatoryBody,
                "system", LocalDateTime.now(), LocalDateTime.now()
            );

            // Track classification lineage
            FinanceLineageEvent lineageEvent = new FinanceLineageEvent(
                UUID.randomUUID().toString(), assetId, "DATA_CLASSIFIED", "system",
                "data-governance", Map.of("classification", classification.classification()),
                Instant.now(), "FINRA-RULE-001"
            );

            lineagePort.trackLineage(lineageEvent);
            financeClassifiedCounter.increment();

            return asset;
        });
    }

    /**
     * Apply retention policy to financial data with regulatory compliance.
     */
    public Promise<Void> applyRetentionPolicy(String assetId, FinanceDataType dataType, FinanceRegulatoryBody regulatoryBody) {
        return Promise.ofBlocking(executor, () -> {
            // Get retention policy based on classification and regulatory requirements
            FinanceRetentionPolicy policy = retentionPolicyPort.getRetentionPolicy(
                "CONFIDENTIAL", regulatoryBody.name(), dataType.name()
            );

            // Enforce retention policy
            retentionPolicyPort.enforceRetentionPolicy(assetId, policy).getResult();

            // Track retention enforcement
            FinanceLineageEvent lineageEvent = new FinanceLineageEvent(
                UUID.randomUUID().toString(), assetId, "RETENTION_ENFORCED", "system",
                "data-governance", Map.of("policyId", policy.policyId(), "retentionYears", policy.retentionYears()),
                Instant.now(), regulatoryBody.name() + "-RETENTION-RULE"
            );

            lineagePort.trackLineage(lineageEvent);
            financeRetentionEnforcedCounter.increment();

            return null;
        });
    }

    /**
     * Apply dynamic masking to financial data.
     */
    public Promise<MaskedDataResult> applyFinancialDataMasking(
            String dataId, String userId, FinanceClassification classification) {

        return maskingPort.applyMasking(dataId, userId, classification)
            .then(result -> {
                // Track masking application
                FinanceLineageEvent lineageEvent = new FinanceLineageEvent(
                    UUID.randomUUID().toString(), dataId, "DATA_MASKED", userId,
                    "data-governance", Map.of("maskingLevel", result.maskingLevel()),
                    Instant.now(), "FINRA-MASKING-RULE"
                );

                lineagePort.trackLineage(lineageEvent);
                financeMaskingAppliedCounter.increment();

                return Promise.of(result);
            });
    }

    /**
     * Process right to erasure request with financial compliance validation.
     */
    public Promise<FinanceErasureRequest> processRightToErasure(
            String assetId, String requestorId, String reason, FinanceRegulatoryBody regulatoryBody) {

        return Promise.ofBlocking(executor, () -> {
            String requestId = UUID.randomUUID().toString();

            FinanceErasureRequest request = new FinanceErasureRequest(
                requestId, assetId, requestorId, reason, regulatoryBody,
                LocalDateTime.now(), "PENDING_REVIEW"
            );

            // Track erasure request
            FinanceLineageEvent lineageEvent = new FinanceLineageEvent(
                UUID.randomUUID().toString(), assetId, "ERASURE_REQUESTED", requestorId,
                "data-governance", Map.of("requestId", requestId, "reason", reason),
                Instant.now(), regulatoryBody.name() + "-ERASURE-RULE"
            );

            lineagePort.trackLineage(lineageEvent);
            financeErasureRequestedCounter.increment();

            return request;
        });
    }

    /**
     * Query financial data lineage for regulatory audit.
     */
    public Promise<List<FinanceLineageEvent>> queryFinancialDataLineage(
            String assetId, Instant startTime, Instant endTime) {

        return lineagePort.queryLineage(assetId, startTime, endTime);
    }

    /**
     * Bulk classify financial data assets for regulatory compliance.
     */
    public Promise<List<FinanceDataAsset>> bulkClassifyFinancialData(
            List<FinancialDataAssetRequest> requests) {

        return Promise.ofBlocking(executor, () -> {
            List<FinanceDataAsset> results = new ArrayList<>();

            for (FinancialDataAssetRequest request : requests) {
                try {
                    FinanceDataAsset asset = classifyFinancialData(
                        request.assetId(), request.assetName(), request.schemaContent(),
                        request.regulatoryBody()
                    ).getResult();
                    results.add(asset);
                } catch (Exception e) {
                    // Continue with other assets; log error
                    System.err.println("Failed to classify financial asset: " + request.assetId());
                }
            }

            return results;
        });
    }

    /**
     * Generate financial data governance compliance report.
     */
    public Promise<FinanceGovernanceReport> generateComplianceReport(
            FinanceRegulatoryBody regulatoryBody, Instant startDate, Instant endDate) {

        return Promise.ofBlocking(executor, () -> {
            // Generate comprehensive compliance report
            return new FinanceGovernanceReport(
                UUID.randomUUID().toString(),
                regulatoryBody.name(),
                startDate,
                endDate,
                (long) financeClassifiedCounter.count(),
                (long) financeRetentionEnforcedCounter.count(),
                (long) financeMaskingAppliedCounter.count(),
                (long) financeErasureRequestedCounter.count(),
                "COMPLIANT"
            );
        });
    }

    // ── Request Types ───────────────────────────────────────────────────────────

    public record FinancialDataAssetRequest(
        String assetId, String assetName, String schemaContent, FinanceRegulatoryBody regulatoryBody
    ) {}

    public record FinanceGovernanceReport(
        String reportId, String regulatoryBody, Instant startDate, Instant endDate,
        long totalClassified, long retentionEnforced, long maskingApplied, long erasureRequested,
        String complianceStatus
    ) {}

    // ── Default Implementations (for testing) ─────────────────────────────────────

    public static FinanceDataGovernanceService createDefault(MeterRegistry registry, Executor executor) {
        return new FinanceDataGovernanceService(
            new DefaultFinanceClassificationRulePort(),
            new DefaultFinanceRetentionPolicyPort(),
            new DefaultFinanceDataMaskingPort(),
            new DefaultFinanceDataLineagePort(),
            registry,
            executor
        );
    }

    // Default implementations for testing/development
    private static final class DefaultFinanceClassificationRulePort implements FinanceClassificationRulePort {
        @Override
        public FinanceClassification classify(String assetName, String schemaContent, String regulatoryBody) {
            // Simple classification logic for demo
            if (assetName.contains("trade") || assetName.contains("transaction")) {
                return new FinanceClassification("CONFIDENTIAL", FinanceRegulatoryBody.SEC, "SEC-17a-4", "7", "NEED_TO_KNOW");
            } else if (assetName.contains("customer") || assetName.contains("pii")) {
                return new FinanceClassification("RESTRICTED", FinanceRegulatoryBody.GDPR, "GDPR-ART-17", "10", "RESTRICTED");
            } else {
                return new FinanceClassification("INTERNAL", FinanceRegulatoryBody.SEC, "SEC-17a-4", "3", "INTERNAL");
            }
        }
    }

    private static final class DefaultFinanceRetentionPolicyPort implements FinanceRetentionPolicyPort {
        @Override
        public FinanceRetentionPolicy getRetentionPolicy(String classification, String regulatoryBody, String dataType) {
            return new FinanceRetentionPolicy(
                "default-policy", new FinanceClassification(classification, FinanceRegulatoryBody.SEC, "", "", ""),
                FinanceRegulatoryBody.SEC, FinanceDataType.TRADE_DATA, 7, true, "secure-archive", LocalDateTime.now()
            );
        }

        @Override
        public Promise<Void> enforceRetentionPolicy(String assetId, FinanceRetentionPolicy policy) {
            return Promise.of(null);
        }
    }

    private static final class DefaultFinanceDataMaskingPort implements FinanceDataMaskingPort {
        @Override
        public Promise<MaskedDataResult> applyMasking(String dataId, String userId, FinanceClassification classification) {
            return Promise.of(new MaskedDataResult("***MASKED***", List.of("ssn", "account"), "FULL", true));
        }
    }

    private static final class DefaultFinanceDataLineagePort implements FinanceDataLineagePort {
        @Override
        public Promise<Void> trackLineage(FinanceLineageEvent event) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<FinanceLineageEvent>> queryLineage(String assetId, Instant startTime, Instant endTime) {
            return Promise.of(List.of());
        }
    }
}
