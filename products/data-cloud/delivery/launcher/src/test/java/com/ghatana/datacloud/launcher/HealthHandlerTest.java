package com.ghatana.datacloud.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.handlers.HealthHandler;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link HealthHandler} readiness probe semantics.
 *
 * <p>Verifies the {@code NOT_CONFIGURED} → {@code UNKNOWN} propagation rules
 * and overall readiness logic documented in the handler (DC-H2).
 *
 * @doc.type class
 * @doc.purpose Verify NOT_CONFIGURED propagation and readiness probe semantics in HealthHandler
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HealthHandler readiness probe semantics (DC-H2)")
class HealthHandlerTest {

    private HttpHandlerSupport httpSupport;

    @BeforeEach
    void setUp() {
        httpSupport = new HttpHandlerSupport(
            new ObjectMapper(), "*", "GET,POST,OPTIONS", "Content-Type,Authorization"
        );
    }

    private HealthHandler handler(Map<String, Supplier<Map<String, Object>>> suppliers) {
        return new HealthHandler(httpSupport, suppliers);
    }

    private HttpRequest dummyRequest() {
        return HttpRequest.get("http://localhost/health").build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Promise<HttpResponse> promise) throws Exception {
        HttpResponse response = promise.getResult();
        String json = response.getBody().getString(StandardCharsets.UTF_8);
        return new ObjectMapper().readValue(json, Map.class);
    }

    // ==================== /health (liveness) ====================

    @Nested
    @DisplayName("/health liveness")
    class LivenessTests {

        @Test
        @DisplayName("always returns UP when process is reachable")
        void livenessAlwaysReturnsUp() throws Exception {
            HealthHandler handler = handler(Map.of());
            Map<String, Object> body = body(handler.handleHealth(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("UP");
        }
    }

    // ==================== /ready readiness ====================

    @Nested
    @DisplayName("/ready readiness probe")
    class ReadinessTests {

        @Test
        @DisplayName("READY when no subsystem suppliers are registered")
        void readyWhenNoSuppliers() throws Exception {
            HealthHandler handler = handler(Map.of());
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("READY");
        }

        @Test
        @DisplayName("READY when all registered subsystems return UP")
        void readyWhenAllSubsystemsUp() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "UP"),
                "event_store", () -> Map.of("status", "UP")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("READY");
        }

        @Test
        @DisplayName("NOT_READY when database subsystem is DOWN")
        void notReadyWhenDatabaseDown() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "DOWN", "error", "connection refused")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("NOT_READY");
        }

        @Test
        @DisplayName("NOT_READY when event_store subsystem is DOWN")
        void notReadyWhenEventStoreDown() throws Exception {
            HealthHandler handler = handler(Map.of(
                "event_store", () -> Map.of("status", "DOWN", "error", "broker unavailable")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("NOT_READY");
        }

        @Test
        @DisplayName("READY when non-critical subsystem is DOWN (only database/event_store are critical)")
        void readyWhenNonCriticalSubsystemDown() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "UP"),
                "cache", () -> Map.of("status", "DOWN")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("READY");
        }
    }

    // ==================== NOT_CONFIGURED propagation ====================

    @Nested
    @DisplayName("NOT_CONFIGURED status propagation")
    class NotConfiguredPropagationTests {

        @Test
        @DisplayName("NOT_CONFIGURED subsystem is reported as-is in /health/detail")
        void notConfiguredSubsystemAppearsInDetailResponse() throws Exception {
            HealthHandler handler = handler(Map.of(
                "voice_gateway", () -> Map.of("status", "NOT_CONFIGURED", "note", "dependency-not-configured")
            ));
            Map<String, Object> body = body(handler.handleHealthDetail(dummyRequest()));

            @SuppressWarnings("unchecked")
            Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
            assertThat(subsystems).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> voiceGateway = (Map<String, Object>) subsystems.get("voice_gateway");
            assertThat(voiceGateway).isNotNull();
            assertThat(voiceGateway.get("status")).isEqualTo("NOT_CONFIGURED");
        }

        @Test
        @DisplayName("NOT_CONFIGURED does not cause overall status to degrade in /health/detail")
        void notConfiguredDoesNotDegradeOverallStatus() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "UP"),
                "voice_gateway", () -> Map.of("status", "NOT_CONFIGURED", "note", "dependency-not-configured")
            ));
            Map<String, Object> body = body(handler.handleHealthDetail(dummyRequest()));

            // NOT_CONFIGURED is not counted as DOWN or DEGRADED → overall stays UP
            assertThat(body.get("status")).isEqualTo("UP");
        }

        @Test
        @DisplayName("NOT_CONFIGURED is remapped to UNKNOWN in /health/deep normalisation")
        void notConfiguredRemappedToUnknownInDeepEndpoint() throws Exception {
            HealthHandler handler = handler(Map.of(
                "voice_gateway", () -> Map.of("status", "NOT_CONFIGURED", "note", "dependency-not-configured")
            ));
            Map<String, Object> body = body(handler.handleHealthDeep(dummyRequest()));

            @SuppressWarnings("unchecked")
            Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
            assertThat(subsystems).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> voiceGateway = (Map<String, Object>) subsystems.get("voice_gateway");
            assertThat(voiceGateway).isNotNull();
            // normalizeDeepSubsystemSnapshot rewrites NOT_CONFIGURED → UNKNOWN
            assertThat(voiceGateway.get("status")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("database NOT_CONFIGURED with expected note 'dependency-not-configured' is treated as READY (planned absence)")
        void databaseNotConfiguredWithExpectedNoteIsReady() throws Exception {
            // The handler treats NOT_CONFIGURED + note=dependency-not-configured as a planned/expected absence
            // (e.g. development mode, optional external database). The service IS ready to serve traffic.
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "NOT_CONFIGURED", "note", "dependency-not-configured")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("READY");
        }

        @Test
        @DisplayName("database NOT_CONFIGURED with unexpected note is treated as NOT_READY (misconfiguration)")
        void databaseNotConfiguredWithUnexpectedNoteIsNotReady() throws Exception {
            // An unexpected note on NOT_CONFIGURED signals an unplanned misconfiguration → NOT_READY
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "NOT_CONFIGURED", "note", "missing-env-var")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("NOT_READY");
        }

        @Test
        @DisplayName("database NOT_CONFIGURED with no note is treated as NOT_READY (uncategorised)")
        void databaseNotConfiguredWithNoNoteIsNotReady() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "NOT_CONFIGURED")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("NOT_READY");
        }

        @Test
        @DisplayName("non-critical NOT_CONFIGURED subsystem does not affect readiness")
        void nonCriticalNotConfiguredDoesNotAffectReadiness() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> Map.of("status", "UP"),
                "policy_engine", () -> Map.of("status", "NOT_CONFIGURED", "note", "dependency-not-configured")
            ));
            Map<String, Object> body = body(handler.handleReady(dummyRequest()));
            assertThat(body.get("status")).isEqualTo("READY");
        }
    }

    // ==================== Subsystem error handling ====================

    @Nested
    @DisplayName("subsystem error handling")
    class SubsystemErrorHandlingTests {

        @Test
        @DisplayName("supplier that throws RuntimeException is captured as DOWN")
        void supplierThrowingRuntimeExceptionCapturedAsDown() throws Exception {
            HealthHandler handler = handler(Map.of(
                "database", () -> { throw new RuntimeException("connect timeout"); }
            ));
            Map<String, Object> body = body(handler.handleHealthDetail(dummyRequest()));

            @SuppressWarnings("unchecked")
            Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
            @SuppressWarnings("unchecked")
            Map<String, Object> db = (Map<String, Object>) subsystems.get("database");
            assertThat(db.get("status")).isEqualTo("DOWN");
            assertThat(db.get("message")).isEqualTo("connect timeout");
        }

        @Test
        @DisplayName("empty health snapshot from supplier is captured as UNKNOWN")
        void emptySnapshotCapturedAsUnknown() throws Exception {
            HealthHandler handler = handler(Map.of(
                "cache", () -> Map.of()
            ));
            Map<String, Object> body = body(handler.handleHealthDetail(dummyRequest()));

            @SuppressWarnings("unchecked")
            Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
            @SuppressWarnings("unchecked")
            Map<String, Object> cache = (Map<String, Object>) subsystems.get("cache");
            assertThat(cache.get("status")).isEqualTo("UNKNOWN");
        }
    }
}
