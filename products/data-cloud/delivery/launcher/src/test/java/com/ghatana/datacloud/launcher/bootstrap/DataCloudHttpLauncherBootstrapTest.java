package com.ghatana.datacloud.launcher.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.launcher.DataCloudTransportStartupException;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * @doc.type class
 * @doc.purpose Verifies standalone HTTP bootstrap startup behavior and typed failure wrapping
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpLauncherBootstrap")
class DataCloudHttpLauncherBootstrapTest {

    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("buildJwtProvider returns null when JWT auth is not configured")
    void buildJwtProviderReturnsNullWhenNotConfigured() { 
        Logger log = mock(Logger.class); 

        JwtTokenProvider provider = DataCloudHttpLauncherBootstrap.buildJwtProvider(Map.of(), log); 

        assertThat(provider).isNull(); 
    }

    @Test
    @DisplayName("buildApiKeyResolver returns null for empty keys in local profile")
    void buildApiKeyResolverReturnsNullForEmptyKeysInLocalProfile() { 
        Logger log = mock(Logger.class); 

        assertThat(DataCloudHttpLauncherBootstrap.buildApiKeyResolver( 
                Map.of( 
                    "DATACLOUD_PROFILE", "local",
                    "DATACLOUD_API_KEYS", " , , "),
                log))
            .isNull(); 
    }

    @Test
    @DisplayName("buildApiKeyResolver fails for empty keys in production profile")
    void buildApiKeyResolverFailsForEmptyKeysInProductionProfile() { 
        Logger log = mock(Logger.class); 

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.buildApiKeyResolver( 
                Map.of( 
                    "DATACLOUD_PROFILE", "production",
                    "DATACLOUD_API_KEYS", " , , "),
                log))
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("must contain at least one non-blank");
    }

            @Test
            @DisplayName("buildApiKeyResolver fails when keys are missing in production profile")
            void buildApiKeyResolverFailsWhenKeysMissingInProductionProfile() {
            Logger log = mock(Logger.class);

            assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
                Map.of("DATACLOUD_PROFILE", "production"),
                log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_API_KEYS environment variable must be set");
            }

            @Test
            @DisplayName("buildApiKeyResolver generates non-secret principal id for key:tenant format")
            void buildApiKeyResolverGeneratesNonSecretPrincipalId() {
            Logger log = mock(Logger.class);
            String rawApiKey = "super-secret-api-key-123456";
            String entry = rawApiKey + ":acme-corp";

            ApiKeyResolver resolver = DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
                Map.of(
                    "DATACLOUD_PROFILE", "production",
                    "DATACLOUD_API_KEYS", entry),
                log);

            Optional<Principal> principal = resolver.resolve(rawApiKey);
            assertThat(principal).isPresent();
            assertThat(principal.orElseThrow().getName()).startsWith("key-");
            assertThat(principal.orElseThrow().getName()).doesNotContain("123456");
            assertThat(principal.orElseThrow().getName()).doesNotContain("secret");
            }

            @Test
            @DisplayName("buildApiKeyResolver binds resolved principal to configured tenant")
            void buildApiKeyResolverBindsPrincipalToConfiguredTenant() {
            Logger log = mock(Logger.class);

            ApiKeyResolver resolver = DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
                Map.of(
                    "DATACLOUD_PROFILE", "production",
                    "DATACLOUD_API_KEYS", "key-a:tenant-alpha,key-b:tenant-beta"),
                log);

            Optional<Principal> principalA = resolver.resolve("key-a");
            Optional<Principal> principalB = resolver.resolve("key-b");
            Optional<Principal> missing = resolver.resolve("key-c");

            assertThat(principalA).isPresent();
            assertThat(principalA.orElseThrow().getTenantId()).isEqualTo("tenant-alpha");
            assertThat(principalB).isPresent();
            assertThat(principalB.orElseThrow().getTenantId()).isEqualTo("tenant-beta");
            assertThat(missing).isEmpty();
            }

            @Test
            @DisplayName("buildApiKeyResolver rejects plain key without tenant in production profile")
            void buildApiKeyResolverRejectsPlainKeyWithoutTenantInProduction() {
            Logger log = mock(Logger.class);

            assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
                Map.of(
                    "DATACLOUD_PROFILE", "production",
                    "DATACLOUD_API_KEYS", "plainkey-no-tenant"),
                log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing a tenant binding");
            }

            @Test
            @DisplayName("buildApiKeyResolver accepts plain key in local profile with service tenant fallback")
            void buildApiKeyResolverAcceptsPlainKeyInLocalProfile() {
            Logger log = mock(Logger.class);

            ApiKeyResolver resolver = DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
                Map.of(
                    "DATACLOUD_PROFILE", "local",
                    "DATACLOUD_API_KEYS", "plain-local-key"),
                log);

            assertThat(resolver).isNotNull();
            Optional<Principal> principal = resolver.resolve("plain-local-key");
            assertThat(principal).isPresent();
            assertThat(principal.orElseThrow().getTenantId()).isEqualTo("service");
            }

    @Test
    @DisplayName("fails fast when auth is missing in non-embedded mode")
    void failsFastWhenAuthMissingInNonEmbeddedMode() {
        Logger log = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.validateAuthenticationAndResolveBindHost(
            false,
            false,
            false,
            "",
            log))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SECURITY ERROR: No authentication configured");
    }

    @Test
    @DisplayName("warns when auth is missing in embedded mode")
    void warnsWhenAuthMissingInEmbeddedMode() {
        Logger log = mock(Logger.class);

        String bindHost = DataCloudHttpLauncherBootstrap.validateAuthenticationAndResolveBindHost(
            false,
            false,
            true,
            "",
            log);

        assertThat(bindHost).isEmpty();
        verify(log).warn("[DC-E2] No authentication configured in embedded/local profile. " +
            "Set DATACLOUD_API_KEYS or DATACLOUD_JWT_SECRET to enable authentication. " +
            "To allow insecure mode, set DATACLOUD_INSECURE_MODE=true.");
    }

    @Test
    @DisplayName("forces loopback bind host in insecure embedded mode when bind host is missing")
    void forcesLoopbackBindHostInInsecureEmbeddedModeWhenMissing() {
        Logger log = mock(Logger.class);

        String bindHost = DataCloudHttpLauncherBootstrap.validateAuthenticationAndResolveBindHost(
            true,
            false,
            true,
            "",
            log);

        assertThat(bindHost).isEqualTo("127.0.0.1");
        verify(log).warn("[DC-E2] DATACLOUD_BIND_HOST not set while running insecure embedded mode; forcing loopback bind host {}", "127.0.0.1");
    }

    @Test
    @DisplayName("rejects insecure embedded mode when bind host is not loopback")
    void rejectsInsecureEmbeddedModeWhenBindHostIsNotLoopback() {
        Logger log = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.validateAuthenticationAndResolveBindHost(
            true,
            false,
            true,
            "0.0.0.0",
            log))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insecure embedded mode requires loopback binding");
    }

    @Test
    @DisplayName("buildJwtProvider creates shared-secret provider when JWT auth is configured")
    void buildJwtProviderCreatesSharedSecretProvider() { 
        Logger log = mock(Logger.class); 

        JwtTokenProvider provider = DataCloudHttpLauncherBootstrap.buildJwtProvider( 
                Map.of( 
                "DATACLOUD_JWT_SECRET", TEST_JWT_SECRET,
                        "DATACLOUD_JWT_TENANT_CLAIM", "tenant_id",
                        "DATACLOUD_JWT_VALIDITY_MS", "60000"),
                log);

        assertThat(provider).isNotNull(); 
        String token = provider.createToken("svc-user", java.util.List.of("reader"), Map.of("tenant_id", "tenant-a"));
        assertThat(provider.validateToken(token)).isTrue(); 
        assertThat(provider.getUserIdFromToken(token)).contains("svc-user");
        assertThat(provider.extractClaims(token).orElseThrow()).containsEntry("tenant_id", "tenant-a"); 
    }

    @Test
    @DisplayName("buildJwtProvider creates JWKS-backed provider when JWKS URL is configured")
    void buildJwtProviderCreatesJwksProvider() throws Exception { 
        Logger log = mock(Logger.class); 

        try (RsaJwksFixture fixture = RsaJwksFixture.create()) { 
            JwtTokenProvider provider = DataCloudHttpLauncherBootstrap.buildJwtProvider( 
                    Map.of( 
                            "DATACLOUD_JWT_JWKS_URL", fixture.jwksUrl(), 
                            "DATACLOUD_JWT_TENANT_CLAIM", "tenant_id"),
                    log);

            assertThat(provider).isNotNull(); 
            String token = fixture.createToken("svc-jwks", java.util.List.of("reader"), Map.of("tenant_id", "tenant-jwks"));
            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getUserIdFromToken(token)).contains("svc-jwks");
            assertThat(provider.extractClaims(token).orElseThrow()).containsEntry("tenant_id", "tenant-jwks"); 
        }
    }

    @Test
    @DisplayName("trace sampling defaults to full coverage in local and staging profiles")
    void traceSamplingDefaultsToFullCoverageInLocalAndStaging() { 
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate(Map.of("DATACLOUD_PROFILE", "local"))) 
                .isEqualTo(1.0); 
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate(Map.of("DATACLOUD_PROFILE", "staging"))) 
                .isEqualTo(1.0); 
    }

    @Test
    @DisplayName("trace sampling defaults to one percent outside local and staging")
    void traceSamplingDefaultsToOnePercentOutsideLocalAndStaging() { 
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate(Map.of("DATACLOUD_PROFILE", "production"))) 
                .isEqualTo(0.01); 
    }

    @Test
    @DisplayName("trace sampling honors explicit environment override")
    void traceSamplingHonorsExplicitOverride() { 
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate( 
                Map.of("DATACLOUD_PROFILE", "production", "DATACLOUD_TRACE_SAMPLING_RATIO", "0.25"))) 
                .isEqualTo(0.25); 
    }

    @Test
    @DisplayName("wires database health subsystem and shutdown hook on successful startup")
    void wiresDatabaseHealthSubsystemAndShutdownHook() throws Exception { 
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class); 
        DataSource dataSource = mock(DataSource.class); 
        Logger log = mock(Logger.class); 
        AtomicReference<Thread> registeredHook = new AtomicReference<>(); 

        when(httpServer.withHealthSubsystem(eq("database"), any())).thenReturn(httpServer);
        when(httpServer.withHealthSubsystem(eq("ai_inference"), any())).thenReturn(httpServer);

        DataCloudHttpLauncherBootstrap.startTransport( 
                httpServer,
                8082,
                true,
            new DataCloudHttpLauncherBootstrap.AiServices(mock(AIModelManager.class), mock(com.ghatana.aiplatform.featurestore.FeatureStoreService.class)), 
                dataSource,
                log,
                registeredHook::set);

        verify(httpServer).withHealthSubsystem(eq("database"), any());
        verify(httpServer).withHealthSubsystem(eq("ai_inference"), any());
        verify(httpServer).start(); 
        assertThat(registeredHook.get()).isNotNull(); 
    }

    @Test
    @DisplayName("does not wire database health subsystem when database is disabled")
    void skipsDatabaseHealthSubsystemWhenDatabaseDisabled() throws Exception { 
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class); 
        Logger log = mock(Logger.class); 

        DataCloudHttpLauncherBootstrap.startTransport( 
                httpServer,
                8082,
                false,
            null,
                null,
                log,
                hook -> {});

        verify(httpServer, never()).withHealthSubsystem(eq("database"), any());
        verify(httpServer, never()).withHealthSubsystem(eq("ai_inference"), any());
        verify(httpServer).start(); 
    }

    @Test
    @DisplayName("wraps server startup failures in typed transport exception")
    void wrapsServerStartupFailuresInTypedTransportException() throws Exception { 
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class); 
        Logger log = mock(Logger.class); 
        doThrow(new IllegalStateException("boom")).when(httpServer).start();

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startTransport( 
                httpServer,
                8082,
                false,
            null,
                null,
                log,
                hook -> {}))
                .isInstanceOf(DataCloudTransportStartupException.class) 
                .hasMessage("Failed to start HTTP server on port 8082")
                .hasCauseInstanceOf(IllegalStateException.class); 

        verify(log).error(eq("Failed to start HTTP server on port {}"), eq(8082), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("ai inference health probe reports startup-initialized services as up")
    void aiInferenceHealthProbeReportsStartupInitializedServicesAsUp() { 
        Map<String, Object> snapshot = DataCloudHttpLauncherBootstrap.buildAiInferenceHealthProbe( 
                new DataCloudHttpLauncherBootstrap.AiServices( 
                        mock(AIModelManager.class), 
                        mock(com.ghatana.aiplatform.featurestore.FeatureStoreService.class))) 
                .get(); 

        assertThat(snapshot).containsEntry("status", "UP"); 
        assertThat(snapshot).containsEntry("model_registry", "UP"); 
        assertThat(snapshot).containsEntry("feature_store", "UP"); 
        assertThat(snapshot).containsEntry("mode", "startup-initialized"); 
    }

    @Test
    @DisplayName("ai inference health probe reports incomplete services as down")
    void aiInferenceHealthProbeReportsIncompleteServicesAsDown() { 
        Map<String, Object> snapshot = DataCloudHttpLauncherBootstrap.buildAiInferenceHealthProbe( 
                new DataCloudHttpLauncherBootstrap.AiServices(mock(AIModelManager.class), null)) 
                .get(); 

        assertThat(snapshot).containsEntry("status", "DOWN"); 
        assertThat(snapshot).containsEntry("model_registry", "UP"); 
        assertThat(snapshot).containsEntry("feature_store", "DOWN"); 
        assertThat(snapshot).containsEntry("message", "AI services incomplete"); 
    }

    @Test
    @DisplayName("fails fast when required database datasource startup fails")
    void failsFastWhenRequiredDatabaseDatasourceStartupFails() { 
        Logger log = mock(Logger.class); 

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startRequiredDatabaseDataSource( 
                log,
                () -> { 
                    throw new IllegalStateException("db unavailable");
                }))
                .isInstanceOf(DataCloudTransportStartupException.class) 
                .hasMessage("Failed to create standalone database DataSource for enabled DB-backed features")
                .hasCauseInstanceOf(IllegalStateException.class); 

        verify(log).error(eq("Failed to create standalone database DataSource for enabled DB-backed features"), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("fails fast when required AI services startup fails")
    void failsFastWhenRequiredAiServicesStartupFails() { 
        Logger log = mock(Logger.class); 

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startRequiredAiServices( 
                log,
                () -> { 
                    throw new IllegalStateException("ai unavailable");
                }))
                .isInstanceOf(DataCloudTransportStartupException.class) 
                .hasMessage("Failed to start AI services while DATACLOUD_AI_ENABLED=true")
                .hasCauseInstanceOf(IllegalStateException.class); 

        verify(log).error(eq("Failed to start AI services while DATACLOUD_AI_ENABLED=true"), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("starts brain services and registers a shutdown hook when brain is enabled")
    void startsBrainServicesAndRegistersShutdownHook() { 
        DataCloudBrain brain = mock(DataCloudBrain.class); 
        DataCloudLearningBridge learningBridge = mock(DataCloudLearningBridge.class); 
        Logger log = mock(Logger.class); 
        AtomicReference<Thread> registeredHook = new AtomicReference<>(); 

        DataCloudHttpLauncherBootstrap.BrainServices services = DataCloudHttpLauncherBootstrap.startBrainServices( 
                log,
                () -> brain, 
                ignoredBrain -> learningBridge,
                registeredHook::set);

        assertThat(services.brain()).isSameAs(brain); 
        assertThat(services.learningBridge()).isSameAs(learningBridge); 
        verify(learningBridge).start(); 
        assertThat(registeredHook.get()).isNotNull(); 
    }

    @Test
    @DisplayName("disables brain services when learning bridge startup fails")
    void disablesBrainServicesWhenStartupFails() { 
        DataCloudBrain brain = mock(DataCloudBrain.class); 
        DataCloudLearningBridge learningBridge = mock(DataCloudLearningBridge.class); 
        Logger log = mock(Logger.class); 
        doThrow(new IllegalStateException("bridge failed")).when(learningBridge).start();

        DataCloudHttpLauncherBootstrap.BrainServices services = DataCloudHttpLauncherBootstrap.startBrainServices( 
                log,
                () -> brain, 
                ignoredBrain -> learningBridge,
                hook -> {});

        assertThat(services.brain()).isNull(); 
        assertThat(services.learningBridge()).isNull(); 
        verify(log).warn( 
                eq("Failed to start brain/learning bridge, continuing without: {}"),
                eq("bridge failed"),
                any(IllegalStateException.class)); 
    }

    @Test
    @DisplayName("starts analytics and report services when analytics is enabled")
    void startsAnalyticsAndReportServices() { 
        AnalyticsQueryEngine analyticsEngine = mock(AnalyticsQueryEngine.class); 
        ReportService reportService = mock(ReportService.class); 
        Logger log = mock(Logger.class); 

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices( 
                log,
                () -> analyticsEngine, 
                ignored -> reportService);

        assertThat(services.analyticsEngine()).isSameAs(analyticsEngine); 
        assertThat(services.reportService()).isSameAs(reportService); 
    }

    @Test
    @DisplayName("keeps analytics engine when report service startup fails")
    void keepsAnalyticsEngineWhenReportStartupFails() { 
        AnalyticsQueryEngine analyticsEngine = mock(AnalyticsQueryEngine.class); 
        Logger log = mock(Logger.class); 

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices( 
                log,
                () -> analyticsEngine, 
                ignored -> {
                    throw new IllegalStateException("report failed");
                });

        assertThat(services.analyticsEngine()).isSameAs(analyticsEngine); 
        assertThat(services.reportService()).isNull(); 
        verify(log).warn( 
                eq("Failed to start report service, continuing without: {}"),
                eq("report failed"),
                any(IllegalStateException.class)); 
    }

    @Test
    @DisplayName("disables analytics services when analytics engine startup fails")
    void disablesAnalyticsServicesWhenAnalyticsStartupFails() { 
        Logger log = mock(Logger.class); 

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices( 
                log,
                () -> { 
                    throw new IllegalStateException("analytics failed");
                },
                ReportService::new);

        assertThat(services.analyticsEngine()).isNull(); 
        assertThat(services.reportService()).isNull(); 
        verify(log).warn( 
                eq("Failed to start analytics engine, continuing without: {}"),
                eq("analytics failed"),
                any(IllegalStateException.class)); 
    }

    private static final class RsaJwksFixture implements AutoCloseable {
        private final RSAKey rsaKey;
        private final HttpServer server;

        private RsaJwksFixture(RSAKey rsaKey, HttpServer server) { 
            this.rsaKey = rsaKey;
            this.server = server;
        }

        static RsaJwksFixture create() throws Exception { 
            RSAKey rsaKey = new RSAKeyGenerator(2048) 
                    .keyID("kid-" + UUID.randomUUID()) 
                    .generate(); 
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); 
            String jwks = new JWKSet(rsaKey.toPublicJWK()).toString(); 
            server.createContext("/jwks", exchange -> { 
                byte[] body = jwks.getBytes(StandardCharsets.UTF_8); 
                exchange.getResponseHeaders().set("Content-Type", "application/json"); 
                exchange.sendResponseHeaders(200, body.length); 
                try (OutputStream outputStream = exchange.getResponseBody()) { 
                    outputStream.write(body); 
                }
            });
            server.start(); 
            return new RsaJwksFixture(rsaKey, server); 
        }

        String jwksUrl() { 
            return "http://localhost:" + server.getAddress().getPort() + "/jwks"; 
        }

        String createToken(String userId, java.util.List<String> roles, Map<String, Object> additionalClaims) 
                throws JOSEException {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder() 
                    .subject(userId) 
                    .claim("roles", roles) 
                    .issueTime(new java.util.Date()) 
                    .expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000L)); 
            additionalClaims.forEach(claimsBuilder::claim); 

            SignedJWT signedJwt = new SignedJWT( 
                    new JWSHeader.Builder(JWSAlgorithm.RS256) 
                            .keyID(rsaKey.getKeyID()) 
                            .type(JOSEObjectType.JWT) 
                            .build(), 
                    claimsBuilder.build()); 
            signedJwt.sign(new RSASSASigner(rsaKey.toPrivateKey())); 
            return signedJwt.serialize(); 
        }

        @Override
        public void close() { 
            server.stop(0); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // P1-03: Secret config validation and rotation-readiness coverage
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buildApiKeyResolver accepts rotated API key set without a restart (P1-03 rotation-readiness)")
    void buildApiKeyResolverAcceptsMultipleKeysForRotation() { // P1-03
        Logger log = mock(Logger.class);

        // Rotation scenario: two keys are present — an old key still valid during
        // the rolling-update window, and a newly issued key for new callers.
        String oldKey = "old-production-key-aaaabbbbccccdddd";
        String newKey = "new-production-key-xxxxyyyyzzzz1111";

        ApiKeyResolver resolver = DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
            Map.of(
                "DATACLOUD_PROFILE", "production",
                "DATACLOUD_API_KEYS", oldKey + "," + newKey),
            log);

        // Both keys must resolve independently — enabling caller-transparent rotation.
        Optional<Principal> oldPrincipal = resolver.resolve(oldKey);
        Optional<Principal> newPrincipal = resolver.resolve(newKey);

        assertThat(oldPrincipal).isPresent();
        assertThat(newPrincipal).isPresent();
        // Each key maps to a distinct principal so audit logs can distinguish them.
        assertThat(oldPrincipal.orElseThrow().getName())
            .isNotEqualTo(newPrincipal.orElseThrow().getName());
    }

    @Test
    @DisplayName("buildApiKeyResolver rejects an expired/unknown key after rotation (P1-03 key invalidation)")
    void buildApiKeyResolverRejectsRevokedKeyAfterRotation() { // P1-03
        Logger log = mock(Logger.class);

        String activeKey = "active-key-aaaabbbbccccddddeeeeffffgggg";
        String revokedKey = "revoked-key-11112222333344445555666677778";

        // Only the active key is configured — simulates post-rotation state.
        ApiKeyResolver resolver = DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
            Map.of(
                "DATACLOUD_PROFILE", "production",
                "DATACLOUD_API_KEYS", activeKey),
            log);

        assertThat(resolver.resolve(activeKey)).isPresent();
        assertThat(resolver.resolve(revokedKey))
            .as("Revoked key must not resolve to a principal after rotation")
            .isEmpty();
    }

    @Test
    @DisplayName("buildApiKeyResolver never logs the raw API key value (P1-03 secret safety)")
    void buildApiKeyResolverNeverLogsRawApiKeyValue() { // P1-03
        Logger log = mock(Logger.class);

        String sensitiveKey = "ultra-secret-key-0000000000000000";

        DataCloudHttpLauncherBootstrap.buildApiKeyResolver(
            Map.of(
                "DATACLOUD_PROFILE", "production",
                "DATACLOUD_API_KEYS", sensitiveKey),
            log);

        // Verify no logger call carried the raw key string.
        // Mockito captures all invocations — none should reference the literal secret.
        org.mockito.verification.VerificationMode never = org.mockito.Mockito.never();
        verify(log, never).info(org.mockito.ArgumentMatchers.contains(sensitiveKey));
        verify(log, never).debug(org.mockito.ArgumentMatchers.contains(sensitiveKey));
        verify(log, never).warn(org.mockito.ArgumentMatchers.contains(sensitiveKey));
        verify(log, never).error(org.mockito.ArgumentMatchers.contains(sensitiveKey));
    }

    @Test
    @DisplayName("buildJwtProvider never logs the raw JWT secret (P1-03 secret safety)")
    void buildJwtProviderNeverLogsRawJwtSecret() { // P1-03
        Logger log = mock(Logger.class);

        String sensitiveSecret = "super-secret-jwt-signing-key-9999";

        DataCloudHttpLauncherBootstrap.buildJwtProvider(
            Map.of(
                "DATACLOUD_JWT_SECRET", sensitiveSecret),
            log);

        verify(log, org.mockito.Mockito.never()).info(org.mockito.ArgumentMatchers.contains(sensitiveSecret));
        verify(log, org.mockito.Mockito.never()).debug(org.mockito.ArgumentMatchers.contains(sensitiveSecret));
        verify(log, org.mockito.Mockito.never()).warn(org.mockito.ArgumentMatchers.contains(sensitiveSecret));
        verify(log, org.mockito.Mockito.never()).error(org.mockito.ArgumentMatchers.contains(sensitiveSecret));
    }
}
