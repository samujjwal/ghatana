/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.identity.IdentityResolutionService;
import com.ghatana.aep.identity.OidcIdentityProvider;
import com.ghatana.aep.identity.SamlIdentityProvider;
import com.ghatana.aep.compliance.ComplianceService;
import com.ghatana.aep.compliance.InMemoryRetentionPolicyEnforcer;
import com.ghatana.aep.compliance.PostgresRetentionPolicyEnforcer;
import com.ghatana.aep.compliance.RetentionPolicyEnforcer;
import com.ghatana.aep.forecasting.ForecastingEngine;
import com.ghatana.aep.forecasting.LinearTrendForecastingEngine;
import com.ghatana.data.governance.ConsentManager;
import com.ghatana.data.governance.DataAccessBroker;
import com.ghatana.data.governance.DefaultDataAccessBroker;
import com.ghatana.data.governance.DefaultPurposeLimitationEnforcer;
import com.ghatana.data.governance.InMemoryConsentManager;
import com.ghatana.data.governance.PostgresConsentManager;
import com.ghatana.data.governance.PurposeLimitationEnforcer;
import com.ghatana.identity.DefaultIdentityService;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.IdentityResolver;
import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryGracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryKillSwitchService;
import com.ghatana.platform.incident.PostgresKillSwitchService;
import com.ghatana.platform.incident.RedisGracefulDegradationManager;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.pac.CircuitBreakingPolicyAsCodeEngine;
import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PostgresPolicyEngine;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.security.analytics.DefaultEgressMonitor;
import com.ghatana.platform.security.analytics.EgressMonitor;
import com.ghatana.platform.security.analytics.PromptInjectionDetector;
import com.ghatana.platform.security.analytics.RegexPromptInjectionDetector;
import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.toolruntime.FailClosedToolSandbox;
import com.ghatana.platform.toolruntime.PolicyBasedToolSandbox;
import com.ghatana.platform.toolruntime.ToolSandbox;
import com.ghatana.platform.toolruntime.approval.ApprovalGateway;
import com.ghatana.platform.toolruntime.approval.InMemoryApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.InMemoryChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.PostgresChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.recertification.InMemoryRecertificationPipeline;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import com.ghatana.core.database.config.JpaConfig;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.flywaydb.core.Flyway;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ActiveJ DI module providing core AEP infrastructure bindings.
 *
 * <p>Wires all governance platform modules into the AEP DI graph:
 * <ul>
 *   <li>{@link IdentityService} / {@link IdentityResolutionService}</li>
 *   <li>{@link ConsentManager} / {@link DataAccessBroker}</li>
 *   <li>{@link EgressMonitor} / {@link PromptInjectionDetector}</li>
 *   <li>{@link PolicyAsCodeEngine}</li>
 *   <li>{@link KillSwitchService} / {@link GracefulDegradationManager}</li>
 *   <li>{@link ApprovalGateway} / {@link ToolSandbox}</li>
 *   <li>{@link RetentionPolicyEnforcer} / {@link ComplianceService}</li>
 *   <li>{@link ForecastingEngine}</li>
 * </ul>
 *
 * <p>In production all major bindings are backed by PostgreSQL (via HikariCP)
 * and Redis (via Jedis). The data source and connection pool are resolved from
 * environment variables:
 * <ul>
 *   <li>{@code AEP_DB_URL} — JDBC URL (e.g. {@code jdbc:postgresql://host:5432/aep})</li>
 *   <li>{@code AEP_DB_USER} — database username</li>
 *   <li>{@code AEP_DB_PASSWORD} — database password</li>
 *   <li>{@code AEP_REDIS_HOST} — Redis host (default: localhost)</li>
 *   <li>{@code AEP_REDIS_PORT} — Redis port (default: 6379)</li>
 * </ul>
 * Falls back to in-memory stubs when {@code AEP_DB_URL} is absent so that
 * development without a local database remains possible.
 *
 * @doc.type class
 * @doc.purpose Core AEP DI wiring — binds all governance platform services
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepCoreModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(AepCoreModule.class);

    @Override
    protected void configure() {
        // No class-level bindings needed; everything is provided via @Provides.
    }

    // ---- Infrastructure -----------------------------------------------------

    @Provides
    DataSource dataSource() {
        return dataSource(System.getenv());
    }

    protected DataSource dataSource(Map<String, String> environment) {
        String dbUrl      = environment.get("AEP_DB_URL");
        String dbUser     = environment.get("AEP_DB_USER");
        String dbPassword = environment.get("AEP_DB_PASSWORD");

        if (dbUrl == null || dbUrl.isBlank()) {
            // Fallback: use a minimal stub DataSource so in-memory impls still compile;
            // real JDBC ops will fail fast if accidentally used without AEP_DB_URL set.
            return null;
        }
        DataSource dataSource = JpaConfig.builder()
            .jdbcUrl(dbUrl)
            .username(dbUser != null ? dbUser : "aep")
            .password(dbPassword != null ? dbPassword : "")
            .poolSize(10)
            .build()
            .createDataSource();

        migrateDatabase(dataSource);
        return dataSource;
    }

    protected void migrateDatabase(DataSource dataSource) {
        log.info("Running AEP database migrations");
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate();
        log.info("AEP database migrations completed successfully");
    }

    @Provides
    Executor blockingExecutor() {
        return Executors.newFixedThreadPool(8,
            r -> { Thread t = new Thread(r, "aep-jdbc"); t.setDaemon(true); return t; });
    }

    @Provides
    Eventloop eventloop() {
        return Eventloop.create();
    }

    @Provides
    JedisPool jedisPool() {
        String redisHost = System.getenv("AEP_REDIS_HOST");
        String redisPort = System.getenv("AEP_REDIS_PORT");
        String host = (redisHost != null && !redisHost.isBlank()) ? redisHost : "localhost";
        int    port = (redisPort != null && !redisPort.isBlank()) ? Integer.parseInt(redisPort) : 6379;
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(5);
        poolConfig.setTestOnBorrow(true);
        return new JedisPool(poolConfig, host, port);
    }

    // ---- Identity -----------------------------------------------------------

    @Provides
    IdentityService identityService() {
        List<IdentityResolver> resolvers = identityResolvers(environment(), dataSource());
        if (resolvers.size() == 1 && resolvers.get(0) instanceof InMemoryIdentityResolver) {
            return new DefaultIdentityService(resolvers.get(0));
        }
        return IdentityResolutionService.identityServiceWithResolvers(resolvers);
    }

    @Provides
    IdentityResolutionService identityResolutionService(IdentityService identityService) {
        return new IdentityResolutionService(identityService);
    }

    protected Map<String, String> environment() {
        return System.getenv();
    }

    List<IdentityResolver> identityResolvers(Map<String, String> environment, DataSource dataSource) {
        ArrayList<IdentityResolver> resolvers = new ArrayList<>();
        OidcIdentityProvider oidcProvider = maybeOidcIdentityProvider(environment);
        if (oidcProvider != null) {
            resolvers.add(oidcProvider);
        }
        SamlIdentityProvider samlProvider = maybeSamlIdentityProvider(environment);
        if (samlProvider != null) {
            resolvers.add(samlProvider);
        }
        resolvers.add(baseIdentityResolver(dataSource));
        return List.copyOf(resolvers);
    }

    protected IdentityResolver baseIdentityResolver(DataSource dataSource) {
        return new InMemoryIdentityResolver();
    }

    protected static OidcIdentityProvider maybeOidcIdentityProvider(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");

        boolean anyOidcSettingPresent = environment.keySet().stream().anyMatch(key -> key.startsWith("AEP_OIDC_"));
        if (!anyOidcSettingPresent) {
            return null;
        }

        String clientId = requireFederationValue(environment, "AEP_OIDC_CLIENT_ID");
        String clientSecret = requireFederationValue(environment, "AEP_OIDC_CLIENT_SECRET");
        String tokenEndpoint = requireFederationValue(environment, "AEP_OIDC_TOKEN_ENDPOINT");
        String subjects = requireFederationValue(environment, "AEP_OIDC_AGENT_SUBJECTS");
        String tokens = requireFederationValue(environment, "AEP_OIDC_AGENT_TOKENS");

        OAuth2Config.Builder configBuilder = OAuth2Config.builder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tokenEndpoint(URI.create(tokenEndpoint))
            .authorizationEndpoint(URI.create(environment.getOrDefault("AEP_OIDC_AUTHORIZATION_ENDPOINT", tokenEndpoint)))
            .redirectUri(URI.create(environment.getOrDefault("AEP_OIDC_REDIRECT_URI", "http://localhost/internal/aep/oidc/callback")));

        String issuerUri = environment.get("AEP_OIDC_ISSUER_URI");
        if (issuerUri != null && !issuerUri.isBlank()) {
            configBuilder.issuerUri(URI.create(issuerUri));
        }

        Map<String, String> subjectMap = parseSimpleMap(subjects);
        Map<String, String> tokenMap = parseSimpleMap(tokens);
        Map<String, String> scopeMap = parseSimpleMap(environment.get("AEP_OIDC_AGENT_SCOPES"));

        ArrayList<OidcIdentityProvider.FederatedAgentRegistration> registrations = new ArrayList<>();
        for (Map.Entry<String, String> entry : subjectMap.entrySet()) {
            AgentKey agentKey = parseAgentKey(entry.getKey());
            String accessToken = tokenMap.get(entry.getKey());
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException(
                    "Missing OIDC access token for federated agent mapping '" + entry.getKey() + "'");
            }

            registrations.add(new OidcIdentityProvider.FederatedAgentRegistration(
                agentKey.tenantId(),
                agentKey.agentId(),
                entry.getValue(),
                accessToken,
                parseScopes(scopeMap.get(entry.getKey()))));
        }

        if (registrations.isEmpty()) {
            throw new IllegalStateException(
                "AEP_OIDC_AGENT_SUBJECTS must define at least one tenant:agent=subject mapping");
        }

        return new OidcIdentityProvider(configBuilder.build(), registrations);
    }

    protected static SamlIdentityProvider maybeSamlIdentityProvider(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");

        boolean anySamlSettingPresent = environment.keySet().stream().anyMatch(key -> key.startsWith("AEP_SAML_"));
        if (!anySamlSettingPresent) {
            return null;
        }

        String identityProviderEntityId = requireFederationValue(environment, "AEP_SAML_IDP_ENTITY_ID");
        String serviceProviderEntityId = requireFederationValue(environment, "AEP_SAML_SP_ENTITY_ID");
        String subjects = requireFederationValue(environment, "AEP_SAML_AGENT_SUBJECTS");
        String assertions = requireFederationValue(environment, "AEP_SAML_AGENT_ASSERTIONS");

        Map<String, String> subjectMap = parseSimpleMap(subjects);
        Map<String, String> assertionMap = parseSimpleMap(assertions);
        Map<String, String> scopeMap = parseSimpleMap(environment.get("AEP_SAML_AGENT_SCOPES"));

        ArrayList<SamlIdentityProvider.FederatedAgentRegistration> registrations = new ArrayList<>();
        for (Map.Entry<String, String> entry : subjectMap.entrySet()) {
            AgentKey agentKey = parseAgentKey(entry.getKey());
            String assertion = assertionMap.get(entry.getKey());
            if (assertion == null || assertion.isBlank()) {
                throw new IllegalStateException(
                    "Missing SAML assertion for federated agent mapping '" + entry.getKey() + "'");
            }

            registrations.add(new SamlIdentityProvider.FederatedAgentRegistration(
                agentKey.tenantId(),
                agentKey.agentId(),
                entry.getValue(),
                assertion,
                parseScopes(scopeMap.get(entry.getKey()))));
        }

        if (registrations.isEmpty()) {
            throw new IllegalStateException(
                "AEP_SAML_AGENT_SUBJECTS must define at least one tenant:agent=subject mapping");
        }

        return new SamlIdentityProvider(identityProviderEntityId, serviceProviderEntityId, registrations);
    }

    protected static String requireFederationValue(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                key + " must be configured when AEP OIDC federation is enabled");
        }
        return value;
    }

    protected static Map<String, String> parseSimpleMap(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Map.of();
        }

        LinkedHashMap<String, String> parsed = new LinkedHashMap<>();
        for (String entry : rawValue.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw new IllegalStateException("Invalid key=value mapping: '" + trimmed + "'");
            }
            parsed.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
        return Map.copyOf(parsed);
    }

    protected static Set<String> parseScopes(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        for (String scope : rawValue.split("\\|")) {
            String trimmed = scope.trim();
            if (!trimmed.isEmpty()) {
                scopes.add(trimmed);
            }
        }
        return Set.copyOf(scopes);
    }

    protected static AgentKey parseAgentKey(String rawKey) {
        int separator = rawKey.indexOf(':');
        if (separator <= 0 || separator == rawKey.length() - 1) {
            throw new IllegalStateException(
                "Federated agent key must use tenantId:agentId format but was '" + rawKey + "'");
        }
        return new AgentKey(rawKey.substring(0, separator), rawKey.substring(separator + 1));
    }

    protected record AgentKey(String tenantId, String agentId) {
    }

    // ---- Data Governance ----------------------------------------------------

    @Provides
    ConsentManager consentManager(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryConsentManager();
        }
        return new PostgresConsentManager(dataSource, executor);
    }

    @Provides
    PurposeLimitationEnforcer purposeLimitationEnforcer() {
        return new DefaultPurposeLimitationEnforcer();
    }

    @Provides
    DataAccessBroker dataAccessBroker(
            ConsentManager consentManager,
            PurposeLimitationEnforcer purposeEnforcer) {
        return new DefaultDataAccessBroker(consentManager, purposeEnforcer);
    }

    // ---- Security Analytics -------------------------------------------------

    @Provides
    EgressMonitor egressMonitor() {
        return new DefaultEgressMonitor();
    }

    @Provides
    PromptInjectionDetector promptInjectionDetector() {
        return new RegexPromptInjectionDetector();
    }

    // ---- Policy-as-Code -----------------------------------------------------

    @Provides
    PolicyAsCodeEngine policyAsCodeEngine(DataSource dataSource, Executor executor, Eventloop eventloop) {
        return new CircuitBreakingPolicyAsCodeEngine(rawPolicyAsCodeEngine(dataSource, executor), eventloop);
    }

    PolicyAsCodeEngine rawPolicyAsCodeEngine(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryPolicyEngine();
        }
        return new PostgresPolicyEngine(dataSource, executor);
    }

    // ---- Incident Response --------------------------------------------------

    @Provides
    KillSwitchService killSwitchService(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryKillSwitchService();
        }
        return new PostgresKillSwitchService(dataSource, executor);
    }

    @Provides
    GracefulDegradationManager gracefulDegradationManager(
            JedisPool jedisPool, DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryGracefulDegradationManager();
        }
        return new RedisGracefulDegradationManager(jedisPool, dataSource, executor);
    }

    // ---- Tool Runtime -------------------------------------------------------

    @Provides
    ApprovalGateway approvalGateway() {
        InMemoryApprovalWorkflow wf = new InMemoryApprovalWorkflow();
        // Register high-risk action types that require human-in-the-loop approval
        wf.requireApproval("DELETE_AGENT");
        wf.requireApproval("DISABLE_PIPELINE");
        wf.requireApproval("POLICY_OVERRIDE");
        wf.requireApproval("BULK_DELETE");
        return wf;
    }

    @Provides
    ToolSandbox toolSandbox(PolicyAsCodeEngine policyEngine) {
        return new PolicyBasedToolSandbox(policyEngine, new FailClosedToolSandbox());
    }

    // ---- Compliance ---------------------------------------------------------

    @Provides
    RetentionPolicyEnforcer retentionPolicyEnforcer(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryRetentionPolicyEnforcer();
        }
        return new PostgresRetentionPolicyEnforcer(dataSource, executor);
    }

    @Provides
    ComplianceService complianceService(
            DataAccessBroker dataAccessBroker,
            RetentionPolicyEnforcer enforcer) {
        return new ComplianceService(dataAccessBroker, enforcer);
    }

    // ---- Change Approval ----------------------------------------------------

    @Provides
    ChangeApprovalWorkflow changeApprovalWorkflow(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryChangeApprovalWorkflow();
        }
        return new PostgresChangeApprovalWorkflow(dataSource, executor);
    }

    // ---- Recertification ----------------------------------------------------

    @Provides
    RecertificationPipeline recertificationPipeline() {
        return new InMemoryRecertificationPipeline();
    }

    // ---- Forecasting --------------------------------------------------------

    @Provides
    ForecastingEngine forecastingEngine() {
        return new LinearTrendForecastingEngine();
    }
}
