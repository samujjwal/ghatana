/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.sdk;

import com.ghatana.platform.core.util.PlatformVersion;
import com.ghatana.platform.core.json.PlatformObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Finance Platform SDK Service.
 *
 * <p>Provides finance-specific SDK abstractions for the Ghatana platform.
 * This service aggregates core platform capabilities with finance-specific
 * extensions and optimizations for financial workflows.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Finance-specific event publishing with compliance metadata</li>
 *   <li>Financial configuration management with audit trails</li>
 *   <li>Regulatory audit logging with finance-specific fields</li>
 *   <li>Financial rule evaluation with compliance checking</li>
 *   <li>Finance-aware authentication with role-based access</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance platform SDK - finance-specific abstractions, compliance, regulatory support
 * @doc.layer finance
 * @doc.pattern Service, SDK
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinancePlatformSdkService {

    // -----------------------------------------------------------------------
    // Finance-Specific Inner Ports
    // -----------------------------------------------------------------------

    public interface FinanceEventBusPort {
        /**
         * Publish a finance event with compliance metadata.
         * Returns the assigned event-id.
         */
        Promise<String> publishFinanceEvent(String topic, String eventType, String payloadJson, 
                                          FinanceComplianceMetadata compliance);

        /** Subscribe a handler to finance events with filtering. */
        void subscribeFinanceEvents(String topic, String consumerGroup, FinanceEventHandler handler);

        @FunctionalInterface
        interface FinanceEventHandler {
            Promise<Void> handle(String eventId, String eventType, String payloadJson, 
                                FinanceComplianceMetadata compliance);
        }
    }

    public interface FinanceConfigBusPort {
        /** Retrieve finance-specific config with audit logging. */
        Promise<String> getFinanceConfig(String namespace, String key);

        /** Update finance config with change tracking. */
        Promise<Void> updateFinanceConfig(String namespace, String key, String value, String changedBy);

        /** List all finance config keys under a namespace. */
        Promise<List<String>> listFinanceConfigKeys(String namespacePrefix);
    }

    public interface FinanceAuditBusPort {
        /** Emit a finance-specific audit event with regulatory fields. */
        Promise<Void> logFinanceEvent(FinanceAuditEvent event);

        /** Query finance audit events with compliance filters. */
        Promise<List<FinanceAuditEvent>> queryFinanceEvents(FinanceAuditQuery query);
    }

    public interface FinanceRulesBusPort {
        /** Evaluate finance rules with compliance checking. */
        Promise<FinanceRuleResult> evaluateFinanceRule(String ruleSetId, FinanceRuleFacts facts);

        /** Validate financial transaction against all applicable rules. */
        Promise<FinanceComplianceResult> validateTransaction(FinanceTransaction transaction);
    }

    public interface FinanceAuthBusPort {
        /** Validate finance-specific JWT with regulatory compliance. */
        Promise<FinanceAuthClaims> validateFinanceToken(String jwt);

        /** Check finance-specific permissions with role hierarchy. */
        Promise<Boolean> hasFinancePermission(String subject, FinancePermission permission);

        /** Validate access to financial data with compliance checks. */
        Promise<Boolean> canAccessFinancialData(String subject, String dataType, String tenantId);
    }

    // -----------------------------------------------------------------------
    // Finance-Specific Client Facades
    // -----------------------------------------------------------------------

    public record FinanceEventClientFacade(FinanceEventBusPort port) {
        public Promise<String> publish(String topic, String eventType, String payloadJson, 
                                     String userId, String tenantId) {
            FinanceComplianceMetadata compliance = new FinanceComplianceMetadata(userId, tenantId, 
                Instant.now(), "FINANCE_TRANSACTION");
            return port.publishFinanceEvent(topic, eventType, payloadJson, compliance);
        }
        
        public void subscribe(String topic, String consumerGroup, FinanceEventBusPort.FinanceEventHandler handler) {
            port.subscribeFinanceEvents(topic, consumerGroup, handler);
        }
    }

    public record FinanceConfigClientFacade(FinanceConfigBusPort port) {
        public Promise<String> get(String namespace, String key) { 
            return port.getFinanceConfig(namespace, key); 
        }
        
        public Promise<Void> update(String namespace, String key, String value, String changedBy) {
            return port.updateFinanceConfig(namespace, key, value, changedBy);
        }
        
        public Promise<List<String>> listKeys(String namespacePrefix) { 
            return port.listFinanceConfigKeys(namespacePrefix); 
        }
    }

    public record FinanceAuditClientFacade(FinanceAuditBusPort port) {
        public Promise<Void> log(FinanceAuditEvent event) {
            return port.logFinanceEvent(event);
        }
        
        public Promise<List<FinanceAuditEvent>> query(FinanceAuditQuery query) {
            return port.queryFinanceEvents(query);
        }
    }

    public record FinanceRulesClientFacade(FinanceRulesBusPort port) {
        public Promise<FinanceRuleResult> evaluate(String ruleSetId, FinanceRuleFacts facts) {
            return port.evaluateFinanceRule(ruleSetId, facts);
        }
        
        public Promise<FinanceComplianceResult> validate(FinanceTransaction transaction) {
            return port.validateTransaction(transaction);
        }
    }

    public record FinanceAuthClientFacade(FinanceAuthBusPort port) {
        public Promise<FinanceAuthClaims> validateToken(String jwt) { 
            return port.validateFinanceToken(jwt); 
        }
        
        public Promise<Boolean> hasPermission(String subject, FinancePermission permission) {
            return port.hasFinancePermission(subject, permission);
        }
        
        public Promise<Boolean> canAccessData(String subject, String dataType, String tenantId) {
            return port.canAccessFinancialData(subject, dataType, tenantId);
        }
    }

    // -----------------------------------------------------------------------
    // Finance-Specific Data Types
    // -----------------------------------------------------------------------

    public record FinanceComplianceMetadata(
        String userId,
        String tenantId,
        Instant timestamp,
        String complianceCategory
    ) {}

    public record FinanceAuditEvent(
        String eventId,
        String service,
        String action,
        String actor,
        String entityId,
        String entityType,
        String beforeJson,
        String afterJson,
        FinanceComplianceMetadata compliance,
        Instant timestamp
    ) {}

    public record FinanceAuditQuery(
        String tenantId,
        String service,
        String action,
        Instant startTime,
        Instant endTime,
        FinanceComplianceMetadata compliance
    ) {}

    public record FinanceRuleFacts(
        String transactionType,
        String amount,
        String currency,
        String counterparty,
        Map<String, Object> additionalFacts
    ) {}

    public record FinanceRuleResult(
        boolean compliant,
        List<String> violations,
        Map<String, Object> ruleOutputs
    ) {}

    public record FinanceComplianceResult(
        boolean approved,
        List<String> complianceChecks,
        List<String> violations,
        String riskLevel
    ) {}

    public record FinanceTransaction(
        String transactionId,
        String type,
        String amount,
        String currency,
        String counterparty,
        String tenantId,
        Map<String, Object> metadata
    ) {}

    public record FinanceAuthClaims(
        String subject,
        List<String> roles,
        List<String> permissions,
        String tenantId,
        Instant expiresAt
    ) {}

    public record FinancePermission(
        String domain,
        String action,
        String resource,
        String condition
    ) {}

    // -----------------------------------------------------------------------
    // Finance SDK Bundle
    // -----------------------------------------------------------------------

    public record FinanceSdkBundle(
        FinanceEventClientFacade eventClient,
        FinanceConfigClientFacade configClient,
        FinanceAuditClientFacade auditClient,
        FinanceRulesClientFacade rulesClient,
        FinanceAuthClientFacade authClient
    ) {}

    // -----------------------------------------------------------------------
    // Registration and Tracking
    // -----------------------------------------------------------------------

    public record FinanceSdkRegistration(
        String registrationId,
        String serviceName,
        String sdkVersion,
        String platformVersion,
        String financeVersion,
        String endpointsJson,
        String registeredAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final FinanceEventBusPort financeEventBusPort;
    private final FinanceConfigBusPort financeConfigBusPort;
    private final FinanceAuditBusPort financeAuditBusPort;
    private final FinanceRulesBusPort financeRulesBusPort;
    private final FinanceAuthBusPort financeAuthBusPort;

    private final Counter financeSdkClientCreatedTotal;
    private final Counter financeSdkEndpointRefreshTotal;

    private static final String PLATFORM_VERSION = PlatformVersion.get().platformVersion();
    private static final String SDK_VERSION = PlatformVersion.get().sdkVersion();
    private static final String FINANCE_VERSION = "1.0.0";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public FinancePlatformSdkService(DataSource dataSource,
                                    Executor executor,
                                    MeterRegistry meterRegistry,
                                    FinanceEventBusPort financeEventBusPort,
                                    FinanceConfigBusPort financeConfigBusPort,
                                    FinanceAuditBusPort financeAuditBusPort,
                                    FinanceRulesBusPort financeRulesBusPort,
                                    FinanceAuthBusPort financeAuthBusPort) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.financeEventBusPort = financeEventBusPort;
        this.financeConfigBusPort = financeConfigBusPort;
        this.financeAuditBusPort = financeAuditBusPort;
        this.financeRulesBusPort = financeRulesBusPort;
        this.financeAuthBusPort = financeAuthBusPort;

        this.financeSdkClientCreatedTotal = Counter.builder("finance.sdk.client.created_total")
                .description("Count of Finance SDK client bundles created")
                .register(meterRegistry);
        this.financeSdkEndpointRefreshTotal = Counter.builder("finance.sdk.endpoint.refresh_total")
                .description("Count of Finance SDK endpoint refreshes")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Create a finance-specific SDK client bundle.
     * Includes finance-specific capabilities and compliance features.
     */
    public Promise<FinanceSdkBundle> createFinanceClientBundle(String callerServiceName) {
        return Promise.ofBlocking(executor, () -> {
            persistFinanceRegistration(callerServiceName);
            financeSdkClientCreatedTotal.increment();
            
            return new FinanceSdkBundle(
                new FinanceEventClientFacade(financeEventBusPort),
                new FinanceConfigClientFacade(financeConfigBusPort),
                new FinanceAuditClientFacade(financeAuditBusPort),
                new FinanceRulesClientFacade(financeRulesBusPort),
                new FinanceAuthClientFacade(financeAuthBusPort)
            );
        });
    }

    /** Return all finance SDK registrations for operator tooling. */
    public Promise<List<FinanceSdkRegistration>> listFinanceRegistrations() {
        return Promise.ofBlocking(executor, this::queryAllFinanceRegistrations);
    }

    /** Get finance SDK metrics and usage statistics. */
    public Promise<FinanceSdkMetrics> getFinanceSdkMetrics() {
        return Promise.ofBlocking(executor, () -> {
            // Implementation would collect finance-specific metrics
            return new FinanceSdkMetrics(
                financeSdkClientCreatedTotal.count(),
                financeSdkEndpointRefreshTotal.count(),
                queryActiveServicesCount()
            );
        });
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static final ObjectMapper MAPPER = PlatformObjectMapper.instance();

    private void persistFinanceRegistration(String serviceName) {
        String sql = """
            INSERT INTO finance_sdk_registrations
                (registration_id, service_name, sdk_version, platform_version, finance_version, registered_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, now())
            ON CONFLICT (service_name) DO UPDATE
              SET sdk_version      = EXCLUDED.sdk_version,
                  platform_version = EXCLUDED.platform_version,
                  finance_version   = EXCLUDED.finance_version,
                  registered_at    = EXCLUDED.registered_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serviceName);
            ps.setString(2, SDK_VERSION);
            ps.setString(3, PLATFORM_VERSION);
            ps.setString(4, FINANCE_VERSION);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist Finance SDK registration for " + serviceName, e);
        }
    }

    private List<FinanceSdkRegistration> queryAllFinanceRegistrations() {
        String sql = """
            SELECT registration_id, service_name, sdk_version, platform_version, finance_version, registered_at::text
              FROM finance_sdk_registrations
             ORDER BY registered_at DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<FinanceSdkRegistration> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new FinanceSdkRegistration(
                    rs.getString("registration_id"),
                    rs.getString("service_name"),
                    rs.getString("sdk_version"),
                    rs.getString("platform_version"),
                    rs.getString("finance_version"),
                    "", // endpoints_json - not used in finance SDK
                    rs.getString("registered_at")
                ));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query Finance SDK registrations", e);
        }
    }

    private int queryActiveServicesCount() {
        String sql = "SELECT COUNT(*) FROM finance_sdk_registrations WHERE registered_at > now() - interval '24 hours'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query active finance services", e);
        }
    }

    // -----------------------------------------------------------------------
    // Metrics
    // -----------------------------------------------------------------------

    public record FinanceSdkMetrics(
        long totalClientsCreated,
        long totalEndpointRefreshes,
        int activeServicesCount
    ) {}
}
