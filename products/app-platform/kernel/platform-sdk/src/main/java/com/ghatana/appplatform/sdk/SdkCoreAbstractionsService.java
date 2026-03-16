package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
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
 * @doc.type    Service
 * @doc.purpose Provides the core platform SDK with typed client wrappers for
 *              K-05 EventBus, K-02 Config, K-07 Audit, K-03 Rules, and K-01 Auth.
 *              Auto-discovers service endpoints from K-02 on startup.
 *              SDK version is aligned with the platform version.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class SdkCoreAbstractionsService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface EventBusPort {
        /**
         * Publish an event to the specified K-05 topic.
         * Returns the assigned event-id.
         */
        Promise<String> publish(String topic, String eventType, String payloadJson, Map<String, String> headers);

        /** Subscribe a handler to a topic/consumer-group pair. */
        void subscribe(String topic, String consumerGroup, EventHandler handler);

        @FunctionalInterface
        interface EventHandler {
            Promise<Void> handle(String eventId, String eventType, String payloadJson, Map<String, String> headers);
        }
    }

    public interface ConfigBusPort {
        /** Retrieve a config value for the given namespace+key. Returns null when absent. */
        Promise<String> get(String namespace, String key);

        /** List all keys under a namespace prefix. */
        Promise<List<String>> listKeys(String namespacePrefix);
    }

    public interface AuditBusPort {
        /** Emit an audit event for the caller's service. */
        Promise<Void> log(String service, String action, String actor, String entityId,
                          String entityType, String beforeJson, String afterJson);
    }

    public interface RulesBusPort {
        /** Evaluate a named rule-set with the provided fact payload. Returns the rule result JSON. */
        Promise<String> evaluate(String ruleSetId, String factsJson);
    }

    public interface AuthBusPort {
        /** Validate a JWT and return the decoded claims JSON, or throw if invalid/expired. */
        Promise<String> validateToken(String jwt);

        /** Check if the subject (user/service) has the specified permission. */
        Promise<Boolean> hasPermission(String subject, String permission);
    }

    public interface SdkEndpointRegistryPort {
        /** Resolve the base URL for a named platform service (discovered from K-02). */
        Promise<String> resolveEndpoint(String serviceName);
    }

    // -----------------------------------------------------------------------
    // Typed client record results
    // -----------------------------------------------------------------------

    public record SdkClientBundle(
        EventClientFacade eventClient,
        ConfigClientFacade configClient,
        AuditClientFacade auditClient,
        RulesClientFacade rulesClient,
        AuthClientFacade authClient
    ) {}

    public record EventClientFacade(EventBusPort port) {
        public Promise<String> publish(String topic, String eventType, String payloadJson) {
            return port.publish(topic, eventType, payloadJson, Map.of());
        }
        public void subscribe(String topic, String consumerGroup, EventBusPort.EventHandler handler) {
            port.subscribe(topic, consumerGroup, handler);
        }
    }

    public record ConfigClientFacade(ConfigBusPort port) {
        public Promise<String> get(String namespace, String key) { return port.get(namespace, key); }
        public Promise<List<String>> listKeys(String namespacePrefix) { return port.listKeys(namespacePrefix); }
    }

    public record AuditClientFacade(AuditBusPort port) {
        public Promise<Void> log(String service, String action, String actor, String entityId,
                                  String entityType, String beforeJson, String afterJson) {
            return port.log(service, action, actor, entityId, entityType, beforeJson, afterJson);
        }
    }

    public record RulesClientFacade(RulesBusPort port) {
        public Promise<String> evaluate(String ruleSetId, String factsJson) {
            return port.evaluate(ruleSetId, factsJson);
        }
    }

    public record AuthClientFacade(AuthBusPort port) {
        public Promise<String> validateToken(String jwt) { return port.validateToken(jwt); }
        public Promise<Boolean> hasPermission(String subject, String permission) {
            return port.hasPermission(subject, permission);
        }
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record SdkRegistration(
        String registrationId,
        String serviceName,
        String sdkVersion,
        String platformVersion,
        String endpointsJson,
        String registeredAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final EventBusPort eventBusPort;
    private final ConfigBusPort configBusPort;
    private final AuditBusPort auditBusPort;
    private final RulesBusPort rulesBusPort;
    private final AuthBusPort authBusPort;
    private final SdkEndpointRegistryPort endpointRegistryPort;

    private final Counter sdkClientCreatedTotal;
    private final Counter sdkEndpointRefreshTotal;

    private static final String PLATFORM_VERSION = "1.0.0";
    private static final String SDK_VERSION      = "1.0.0";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public SdkCoreAbstractionsService(DataSource dataSource,
                                       Executor executor,
                                       MeterRegistry meterRegistry,
                                       EventBusPort eventBusPort,
                                       ConfigBusPort configBusPort,
                                       AuditBusPort auditBusPort,
                                       RulesBusPort rulesBusPort,
                                       AuthBusPort authBusPort,
                                       SdkEndpointRegistryPort endpointRegistryPort) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.eventBusPort           = eventBusPort;
        this.configBusPort          = configBusPort;
        this.auditBusPort           = auditBusPort;
        this.rulesBusPort           = rulesBusPort;
        this.authBusPort            = authBusPort;
        this.endpointRegistryPort   = endpointRegistryPort;

        this.sdkClientCreatedTotal  = Counter.builder("sdk.client.created_total")
                .description("Count of SDK client bundles created")
                .register(meterRegistry);
        this.sdkEndpointRefreshTotal = Counter.builder("sdk.endpoint.refresh_total")
                .description("Count of endpoint refreshes from K-02")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Create a typed SDK client bundle for a service.
     * Auto-discovers downstream endpoints from K-02.
     */
    public Promise<SdkClientBundle> createClientBundle(String callerServiceName) {
        return Promise.ofBlocking(executor, () -> {
            String endpointsJson = resolveEndpointsBlocking(callerServiceName);
            persistRegistration(callerServiceName, endpointsJson);
            sdkClientCreatedTotal.increment();
            return new SdkClientBundle(
                new EventClientFacade(eventBusPort),
                new ConfigClientFacade(configBusPort),
                new AuditClientFacade(auditBusPort),
                new RulesClientFacade(rulesBusPort),
                new AuthClientFacade(authBusPort)
            );
        });
    }

    /** Force refresh of service endpoint cache from K-02. */
    public Promise<Void> refreshEndpoints(String callerServiceName) {
        return Promise.ofBlocking(executor, () -> {
            String endpointsJson = resolveEndpointsBlocking(callerServiceName);
            updateEndpointsBlocking(callerServiceName, endpointsJson);
            sdkEndpointRefreshTotal.increment();
            return null;
        });
    }

    /** Return all SDK registrations (for operator tooling). */
    public Promise<List<SdkRegistration>> listRegistrations() {
        return Promise.ofBlocking(executor, this::queryAllRegistrations);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String resolveEndpointsBlocking(String serviceName) {
        // In production this resolves K-02 config keys like
        // "sdk.endpoints.<eventbus>", "sdk.endpoints.<config>", etc.
        // Returning a stub JSON here for the framework skeleton.
        return "{\"event_bus\":\"http://event-bus:8080\",\"config\":\"http://config-engine:8080\"," +
               "\"audit\":\"http://audit-trail:8080\",\"rules\":\"http://rules-engine:8080\"," +
               "\"auth\":\"http://iam:8080\"}";
    }

    private void persistRegistration(String serviceName, String endpointsJson) {
        String sql = """
            INSERT INTO sdk_registrations
                (registration_id, service_name, sdk_version, platform_version, endpoints_json, registered_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?::jsonb, now())
            ON CONFLICT (service_name) DO UPDATE
              SET sdk_version      = EXCLUDED.sdk_version,
                  platform_version = EXCLUDED.platform_version,
                  endpoints_json   = EXCLUDED.endpoints_json,
                  registered_at    = EXCLUDED.registered_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serviceName);
            ps.setString(2, SDK_VERSION);
            ps.setString(3, PLATFORM_VERSION);
            ps.setString(4, endpointsJson);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist SDK registration for " + serviceName, e);
        }
    }

    private void updateEndpointsBlocking(String serviceName, String endpointsJson) {
        String sql = """
            UPDATE sdk_registrations
               SET endpoints_json = ?::jsonb, registered_at = now()
             WHERE service_name = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, endpointsJson);
            ps.setString(2, serviceName);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update SDK endpoints for " + serviceName, e);
        }
    }

    private List<SdkRegistration> queryAllRegistrations() {
        String sql = """
            SELECT registration_id, service_name, sdk_version, platform_version,
                   endpoints_json::text, registered_at::text
              FROM sdk_registrations
             ORDER BY registered_at DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<SdkRegistration> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new SdkRegistration(
                    rs.getString("registration_id"),
                    rs.getString("service_name"),
                    rs.getString("sdk_version"),
                    rs.getString("platform_version"),
                    rs.getString("endpoints_json"),
                    rs.getString("registered_at")
                ));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query SDK registrations", e);
        }
    }
}
