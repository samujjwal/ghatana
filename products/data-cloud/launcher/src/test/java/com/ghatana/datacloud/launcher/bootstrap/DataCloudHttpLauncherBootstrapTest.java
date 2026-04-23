package com.ghatana.datacloud.launcher.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.launcher.DataCloudTransportStartupException;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.ai.AIModelManager;
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
    void buildJwtProviderReturnsNullWhenNotConfigured() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        JwtTokenProvider provider = DataCloudHttpLauncherBootstrap.buildJwtProvider(Map.of(), log); // GH-90000

        assertThat(provider).isNull(); // GH-90000
    }

    @Test
    @DisplayName("buildApiKeyResolver returns null for empty keys in local profile")
    void buildApiKeyResolverReturnsNullForEmptyKeysInLocalProfile() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        assertThat(DataCloudHttpLauncherBootstrap.buildApiKeyResolver( // GH-90000
                Map.of( // GH-90000
                    "DATACLOUD_PROFILE", "local",
                    "DATACLOUD_API_KEYS", " , , "),
                log))
            .isNull(); // GH-90000
    }

    @Test
    @DisplayName("buildApiKeyResolver fails for empty keys in production profile")
    void buildApiKeyResolverFailsForEmptyKeysInProductionProfile() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.buildApiKeyResolver( // GH-90000
                Map.of( // GH-90000
                    "DATACLOUD_PROFILE", "production",
                    "DATACLOUD_API_KEYS", " , , "),
                log))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("must contain at least one non-blank API key");
    }

    @Test
    @DisplayName("buildJwtProvider creates shared-secret provider when JWT auth is configured")
    void buildJwtProviderCreatesSharedSecretProvider() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        JwtTokenProvider provider = DataCloudHttpLauncherBootstrap.buildJwtProvider( // GH-90000
                Map.of( // GH-90000
                "DATACLOUD_JWT_SECRET", TEST_JWT_SECRET,
                        "DATACLOUD_JWT_TENANT_CLAIM", "tenant_id",
                        "DATACLOUD_JWT_VALIDITY_MS", "60000"),
                log);

        assertThat(provider).isNotNull(); // GH-90000
        String token = provider.createToken("svc-user", java.util.List.of("reader"), Map.of("tenant_id", "tenant-a"));
        assertThat(provider.validateToken(token)).isTrue(); // GH-90000
        assertThat(provider.getUserIdFromToken(token)).contains("svc-user");
        assertThat(provider.extractClaims(token).orElseThrow()).containsEntry("tenant_id", "tenant-a"); // GH-90000
    }

    @Test
    @DisplayName("buildJwtProvider creates JWKS-backed provider when JWKS URL is configured")
    void buildJwtProviderCreatesJwksProvider() throws Exception { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        try (RsaJwksFixture fixture = RsaJwksFixture.create()) { // GH-90000
            JwtTokenProvider provider = DataCloudHttpLauncherBootstrap.buildJwtProvider( // GH-90000
                    Map.of( // GH-90000
                            "DATACLOUD_JWT_JWKS_URL", fixture.jwksUrl(), // GH-90000
                            "DATACLOUD_JWT_TENANT_CLAIM", "tenant_id"),
                    log);

            assertThat(provider).isNotNull(); // GH-90000
            String token = fixture.createToken("svc-jwks", java.util.List.of("reader"), Map.of("tenant_id", "tenant-jwks"));
            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)).contains("svc-jwks");
            assertThat(provider.extractClaims(token).orElseThrow()).containsEntry("tenant_id", "tenant-jwks"); // GH-90000
        }
    }

    @Test
    @DisplayName("trace sampling defaults to full coverage in local and staging profiles")
    void traceSamplingDefaultsToFullCoverageInLocalAndStaging() { // GH-90000
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate(Map.of("DATACLOUD_PROFILE", "local"))) // GH-90000
                .isEqualTo(1.0); // GH-90000
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate(Map.of("DATACLOUD_PROFILE", "staging"))) // GH-90000
                .isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("trace sampling defaults to one percent outside local and staging")
    void traceSamplingDefaultsToOnePercentOutsideLocalAndStaging() { // GH-90000
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate(Map.of("DATACLOUD_PROFILE", "production"))) // GH-90000
                .isEqualTo(0.01); // GH-90000
    }

    @Test
    @DisplayName("trace sampling honors explicit environment override")
    void traceSamplingHonorsExplicitOverride() { // GH-90000
        assertThat(DataCloudHttpLauncherBootstrap.resolveTraceSamplingRate( // GH-90000
                Map.of("DATACLOUD_PROFILE", "production", "DATACLOUD_TRACE_SAMPLING_RATIO", "0.25"))) // GH-90000
                .isEqualTo(0.25); // GH-90000
    }

    @Test
    @DisplayName("wires database health subsystem and shutdown hook on successful startup")
    void wiresDatabaseHealthSubsystemAndShutdownHook() throws Exception { // GH-90000
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class); // GH-90000
        DataSource dataSource = mock(DataSource.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000
        AtomicReference<Thread> registeredHook = new AtomicReference<>(); // GH-90000

        when(httpServer.withHealthSubsystem(eq("database"), any())).thenReturn(httpServer);
        when(httpServer.withHealthSubsystem(eq("ai_inference"), any())).thenReturn(httpServer);

        DataCloudHttpLauncherBootstrap.startTransport( // GH-90000
                httpServer,
                8082,
                true,
            new DataCloudHttpLauncherBootstrap.AiServices(mock(AIModelManager.class), mock(com.ghatana.aiplatform.featurestore.FeatureStoreService.class)), // GH-90000
                dataSource,
                log,
                registeredHook::set);

        verify(httpServer).withHealthSubsystem(eq("database"), any());
        verify(httpServer).withHealthSubsystem(eq("ai_inference"), any());
        verify(httpServer).start(); // GH-90000
        assertThat(registeredHook.get()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("does not wire database health subsystem when database is disabled")
    void skipsDatabaseHealthSubsystemWhenDatabaseDisabled() throws Exception { // GH-90000
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        DataCloudHttpLauncherBootstrap.startTransport( // GH-90000
                httpServer,
                8082,
                false,
            null,
                null,
                log,
                hook -> {});

        verify(httpServer, never()).withHealthSubsystem(eq("database"), any());
        verify(httpServer, never()).withHealthSubsystem(eq("ai_inference"), any());
        verify(httpServer).start(); // GH-90000
    }

    @Test
    @DisplayName("wraps server startup failures in typed transport exception")
    void wrapsServerStartupFailuresInTypedTransportException() throws Exception { // GH-90000
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000
        doThrow(new IllegalStateException("boom")).when(httpServer).start();

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startTransport( // GH-90000
                httpServer,
                8082,
                false,
            null,
                null,
                log,
                hook -> {}))
                .isInstanceOf(DataCloudTransportStartupException.class) // GH-90000
                .hasMessage("Failed to start HTTP server on port 8082")
                .hasCauseInstanceOf(IllegalStateException.class); // GH-90000

        verify(log).error(eq("Failed to start HTTP server on port {}"), eq(8082), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("ai inference health probe reports startup-initialized services as up")
    void aiInferenceHealthProbeReportsStartupInitializedServicesAsUp() { // GH-90000
        Map<String, Object> snapshot = DataCloudHttpLauncherBootstrap.buildAiInferenceHealthProbe( // GH-90000
                new DataCloudHttpLauncherBootstrap.AiServices( // GH-90000
                        mock(AIModelManager.class), // GH-90000
                        mock(com.ghatana.aiplatform.featurestore.FeatureStoreService.class))) // GH-90000
                .get(); // GH-90000

        assertThat(snapshot).containsEntry("status", "UP"); // GH-90000
        assertThat(snapshot).containsEntry("model_registry", "UP"); // GH-90000
        assertThat(snapshot).containsEntry("feature_store", "UP"); // GH-90000
        assertThat(snapshot).containsEntry("mode", "startup-initialized"); // GH-90000
    }

    @Test
    @DisplayName("ai inference health probe reports incomplete services as down")
    void aiInferenceHealthProbeReportsIncompleteServicesAsDown() { // GH-90000
        Map<String, Object> snapshot = DataCloudHttpLauncherBootstrap.buildAiInferenceHealthProbe( // GH-90000
                new DataCloudHttpLauncherBootstrap.AiServices(mock(AIModelManager.class), null)) // GH-90000
                .get(); // GH-90000

        assertThat(snapshot).containsEntry("status", "DOWN"); // GH-90000
        assertThat(snapshot).containsEntry("model_registry", "UP"); // GH-90000
        assertThat(snapshot).containsEntry("feature_store", "DOWN"); // GH-90000
        assertThat(snapshot).containsEntry("message", "AI services incomplete"); // GH-90000
    }

    @Test
    @DisplayName("fails fast when required database datasource startup fails")
    void failsFastWhenRequiredDatabaseDatasourceStartupFails() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startRequiredDatabaseDataSource( // GH-90000
                log,
                () -> { // GH-90000
                    throw new IllegalStateException("db unavailable");
                }))
                .isInstanceOf(DataCloudTransportStartupException.class) // GH-90000
                .hasMessage("Failed to create standalone database DataSource for enabled DB-backed features")
                .hasCauseInstanceOf(IllegalStateException.class); // GH-90000

        verify(log).error(eq("Failed to create standalone database DataSource for enabled DB-backed features"), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("fails fast when required AI services startup fails")
    void failsFastWhenRequiredAiServicesStartupFails() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startRequiredAiServices( // GH-90000
                log,
                () -> { // GH-90000
                    throw new IllegalStateException("ai unavailable");
                }))
                .isInstanceOf(DataCloudTransportStartupException.class) // GH-90000
                .hasMessage("Failed to start AI services while DATACLOUD_AI_ENABLED=true")
                .hasCauseInstanceOf(IllegalStateException.class); // GH-90000

        verify(log).error(eq("Failed to start AI services while DATACLOUD_AI_ENABLED=true"), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("starts brain services and registers a shutdown hook when brain is enabled")
    void startsBrainServicesAndRegistersShutdownHook() { // GH-90000
        DataCloudBrain brain = mock(DataCloudBrain.class); // GH-90000
        DataCloudLearningBridge learningBridge = mock(DataCloudLearningBridge.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000
        AtomicReference<Thread> registeredHook = new AtomicReference<>(); // GH-90000

        DataCloudHttpLauncherBootstrap.BrainServices services = DataCloudHttpLauncherBootstrap.startBrainServices( // GH-90000
                log,
                () -> brain, // GH-90000
                ignoredBrain -> learningBridge,
                registeredHook::set);

        assertThat(services.brain()).isSameAs(brain); // GH-90000
        assertThat(services.learningBridge()).isSameAs(learningBridge); // GH-90000
        verify(learningBridge).start(); // GH-90000
        assertThat(registeredHook.get()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("disables brain services when learning bridge startup fails")
    void disablesBrainServicesWhenStartupFails() { // GH-90000
        DataCloudBrain brain = mock(DataCloudBrain.class); // GH-90000
        DataCloudLearningBridge learningBridge = mock(DataCloudLearningBridge.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000
        doThrow(new IllegalStateException("bridge failed")).when(learningBridge).start();

        DataCloudHttpLauncherBootstrap.BrainServices services = DataCloudHttpLauncherBootstrap.startBrainServices( // GH-90000
                log,
                () -> brain, // GH-90000
                ignoredBrain -> learningBridge,
                hook -> {});

        assertThat(services.brain()).isNull(); // GH-90000
        assertThat(services.learningBridge()).isNull(); // GH-90000
        verify(log).warn( // GH-90000
                eq("Failed to start brain/learning bridge, continuing without: {}"),
                eq("bridge failed"),
                any(IllegalStateException.class)); // GH-90000
    }

    @Test
    @DisplayName("starts analytics and report services when analytics is enabled")
    void startsAnalyticsAndReportServices() { // GH-90000
        AnalyticsQueryEngine analyticsEngine = mock(AnalyticsQueryEngine.class); // GH-90000
        ReportService reportService = mock(ReportService.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices( // GH-90000
                log,
                () -> analyticsEngine, // GH-90000
                ignored -> reportService);

        assertThat(services.analyticsEngine()).isSameAs(analyticsEngine); // GH-90000
        assertThat(services.reportService()).isSameAs(reportService); // GH-90000
    }

    @Test
    @DisplayName("keeps analytics engine when report service startup fails")
    void keepsAnalyticsEngineWhenReportStartupFails() { // GH-90000
        AnalyticsQueryEngine analyticsEngine = mock(AnalyticsQueryEngine.class); // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices( // GH-90000
                log,
                () -> analyticsEngine, // GH-90000
                ignored -> {
                    throw new IllegalStateException("report failed");
                });

        assertThat(services.analyticsEngine()).isSameAs(analyticsEngine); // GH-90000
        assertThat(services.reportService()).isNull(); // GH-90000
        verify(log).warn( // GH-90000
                eq("Failed to start report service, continuing without: {}"),
                eq("report failed"),
                any(IllegalStateException.class)); // GH-90000
    }

    @Test
    @DisplayName("disables analytics services when analytics engine startup fails")
    void disablesAnalyticsServicesWhenAnalyticsStartupFails() { // GH-90000
        Logger log = mock(Logger.class); // GH-90000

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices( // GH-90000
                log,
                () -> { // GH-90000
                    throw new IllegalStateException("analytics failed");
                },
                ReportService::new);

        assertThat(services.analyticsEngine()).isNull(); // GH-90000
        assertThat(services.reportService()).isNull(); // GH-90000
        verify(log).warn( // GH-90000
                eq("Failed to start analytics engine, continuing without: {}"),
                eq("analytics failed"),
                any(IllegalStateException.class)); // GH-90000
    }

    private static final class RsaJwksFixture implements AutoCloseable {
        private final RSAKey rsaKey;
        private final HttpServer server;

        private RsaJwksFixture(RSAKey rsaKey, HttpServer server) { // GH-90000
            this.rsaKey = rsaKey;
            this.server = server;
        }

        static RsaJwksFixture create() throws Exception { // GH-90000
            RSAKey rsaKey = new RSAKeyGenerator(2048) // GH-90000
                    .keyID("kid-" + UUID.randomUUID()) // GH-90000
                    .generate(); // GH-90000
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); // GH-90000
            String jwks = new JWKSet(rsaKey.toPublicJWK()).toString(); // GH-90000
            server.createContext("/jwks", exchange -> { // GH-90000
                byte[] body = jwks.getBytes(StandardCharsets.UTF_8); // GH-90000
                exchange.getResponseHeaders().set("Content-Type", "application/json"); // GH-90000
                exchange.sendResponseHeaders(200, body.length); // GH-90000
                try (OutputStream outputStream = exchange.getResponseBody()) { // GH-90000
                    outputStream.write(body); // GH-90000
                }
            });
            server.start(); // GH-90000
            return new RsaJwksFixture(rsaKey, server); // GH-90000
        }

        String jwksUrl() { // GH-90000
            return "http://localhost:" + server.getAddress().getPort() + "/jwks"; // GH-90000
        }

        String createToken(String userId, java.util.List<String> roles, Map<String, Object> additionalClaims) // GH-90000
                throws JOSEException {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder() // GH-90000
                    .subject(userId) // GH-90000
                    .claim("roles", roles) // GH-90000
                    .issueTime(new java.util.Date()) // GH-90000
                    .expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000L)); // GH-90000
            additionalClaims.forEach(claimsBuilder::claim); // GH-90000

            SignedJWT signedJwt = new SignedJWT( // GH-90000
                    new JWSHeader.Builder(JWSAlgorithm.RS256) // GH-90000
                            .keyID(rsaKey.getKeyID()) // GH-90000
                            .type(JOSEObjectType.JWT) // GH-90000
                            .build(), // GH-90000
                    claimsBuilder.build()); // GH-90000
            signedJwt.sign(new RSASSASigner(rsaKey.toPrivateKey())); // GH-90000
            return signedJwt.serialize(); // GH-90000
        }

        @Override
        public void close() { // GH-90000
            server.stop(0); // GH-90000
        }
    }
}
