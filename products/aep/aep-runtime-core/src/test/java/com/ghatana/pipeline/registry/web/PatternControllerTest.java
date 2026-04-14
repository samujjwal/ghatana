package com.ghatana.pipeline.registry.web;

import com.ghatana.pipeline.registry.model.Pattern;
import com.ghatana.pipeline.registry.service.PatternService;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatternController}.
 *
 * @doc.type test
 * @doc.purpose Verify HTTP responses for pattern CRUD and lifecycle endpoints
 * @doc.layer product
 * @doc.pattern ControllerTest
 */
@DisplayName("PatternController tests")
@ExtendWith(MockitoExtension.class)
class PatternControllerTest extends EventloopTestBase {

    @Mock
    private PatternService patternService;

    private PatternController controller;
    private static final TenantId TENANT = TenantId.of("test-tenant");
    private static final String USER   = "test-user";

    @BeforeEach
    void setUp() {
        controller = new PatternController(patternService);
    }

    // ── createPattern ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createPattern: valid body → 201 Created")
    void createPatternValidReturns201() {
        Pattern created = Pattern.builder()
                .id("pat-1")
                .tenantId(TENANT)
                .name("fraud-detect")
                .specification("IF amount > 1000 THEN ALERT")
                .build();
        when(patternService.register(any(Pattern.class), eq(USER)))
                .thenReturn(Promise.of(created));

        String json = "{\"name\":\"fraud-detect\",\"specification\":\"IF amount > 1000 THEN ALERT\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/patterns")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.createPattern(request, TENANT, USER));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("createPattern: missing name → 400 Bad Request")
    void createPatternMissingNameReturns400() {
        String json = "{\"specification\":\"IF amount > 1000 THEN ALERT\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/patterns")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.createPattern(request, TENANT, USER));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("createPattern: missing specification → 400 Bad Request")
    void createPatternMissingSpecificationReturns400() {
        String json = "{\"name\":\"fraud-detect\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/patterns")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.createPattern(request, TENANT, USER));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("createPattern: service failure → 500 Internal Server Error")
    void createPatternServiceFailureReturns500() {
        when(patternService.register(any(Pattern.class), eq(USER)))
                .thenReturn(Promise.ofException(new RuntimeException("DB error")));

        String json = "{\"name\":\"fraud-detect\",\"specification\":\"IF x THEN y\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/patterns")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.createPattern(request, TENANT, USER));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ── listPatterns ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("listPatterns: tenant has patterns → 200 OK")
    void listPatternsReturns200() {
        Pattern p = Pattern.builder().id("pat-1").tenantId(TENANT).name("p1").specification("spec1").build();
        when(patternService.list(TENANT, null)).thenReturn(Promise.of(List.of(p)));

        HttpResponse response = runPromise(() -> controller.listPatterns(TENANT, null));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("listPatterns: no patterns → 200 OK with empty list")
    void listPatternsEmptyReturns200() {
        when(patternService.list(TENANT, "ACTIVE")).thenReturn(Promise.of(List.of()));

        HttpResponse response = runPromise(() -> controller.listPatterns(TENANT, "ACTIVE"));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ── getPattern ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPattern: found → 200 OK")
    void getPatternFoundReturns200() {
        Pattern p = Pattern.builder().id("pat-1").tenantId(TENANT).name("p1").specification("s").build();
        when(patternService.getById("pat-1", TENANT)).thenReturn(Promise.of(Optional.of(p)));

        HttpResponse response = runPromise(() -> controller.getPattern("pat-1", TENANT));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("getPattern: not found → 404 Not Found")
    void getPatternNotFoundReturns404() {
        when(patternService.getById("not-exist", TENANT)).thenReturn(Promise.of(Optional.empty()));

        HttpResponse response = runPromise(() -> controller.getPattern("not-exist", TENANT));

        assertThat(response.getCode()).isEqualTo(404);
    }

    // ── deletePattern ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("deletePattern: exists → 204 No Content")
    void deletePatternReturns204() {
        when(patternService.delete("pat-1", TENANT, USER)).thenReturn(Promise.of(null));

        HttpResponse response = runPromise(() -> controller.deletePattern("pat-1", TENANT, USER));

        assertThat(response.getCode()).isEqualTo(204);
    }

    // ── activatePattern / deactivatePattern ────────────────────────────────────

    @Test
    @DisplayName("activatePattern: exists → 200 OK")
    void activatePatternReturns200() {
        when(patternService.activate("pat-1", TENANT, USER)).thenReturn(Promise.of(null));

        HttpResponse response = runPromise(() -> controller.activatePattern("pat-1", TENANT, USER));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("deactivatePattern: exists → 200 OK")
    void deactivatePatternReturns200() {
        when(patternService.deactivate("pat-1", TENANT, USER)).thenReturn(Promise.of(null));

        HttpResponse response = runPromise(() -> controller.deactivatePattern("pat-1", TENANT, USER));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
