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
            .hasMessageContaining("must contain at least one non-blank API key");
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
                byte[] body = jwks.getBytes();
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
}
