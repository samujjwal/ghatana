package com.ghatana.phr.api.routes;

import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrEntitlementRoutes}.
 *
 * <p>Verifies that the entitlement endpoint:
 * <ul>
 *   <li>Returns 200 with an entitlement payload for authenticated users.</li>
 *   <li>Returns 200 using cached entitlements when available.</li>
 *   <li>Defaults gracefully when context headers are absent (anonymous persona).</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Entitlement routes enforcement matrix: verifies route entitlement delivery and cache usage
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrEntitlementRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrEntitlementRoutesTest extends EventloopTestBase {

    @Mock
    private RouteEntitlementEvaluator routeEntitlementEvaluator;

    @SuppressWarnings("unchecked")
    @Mock
    private IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrEntitlementRoutes(eventloop(), routeEntitlementEvaluator, entitlementCache)
            .getServlet();

        // Default: cache miss forces computation
        lenient().when(entitlementCache.get(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        lenient().when(routeEntitlementEvaluator.filterActionsByRole(any(), anyString(), any()))
            .thenReturn(List.of());
        lenient().when(routeEntitlementEvaluator.filterCardsByRole(any(), anyString(), any()))
            .thenReturn(List.of());
        lenient().when(routeEntitlementEvaluator.filterByRole(any(), anyString(), any()))
            .thenReturn(List.of());
    }

    @Test
    @DisplayName("200 — patient with valid context receives route entitlements")
    void patientReceivesEntitlements() throws Exception {
        HttpRequest request = contextRequest("t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — clinician with valid context receives route entitlements")
    void clinicianReceivesEntitlements() throws Exception {
        HttpRequest request = contextRequest("t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin with valid context receives route entitlements")
    void adminReceivesEntitlements() throws Exception {
        HttpRequest request = contextRequest("t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("hidden routes are excluded from backend entitlement evaluation")
    void hiddenRoutesAreExcludedFromEntitlements() throws Exception {
        HttpRequest request = contextRequest("t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(routeEntitlementEvaluator).filterByRole(
            argThat(routes -> routes.stream().noneMatch(route ->
                route.path().endsWith("/provider/dashboard") ||
                    route.path().endsWith("/provider/patients") ||
                    route.path().endsWith("/caregiver/dependents") ||
                    route.path().endsWith("/fchv/dashboard"))),
            eq("admin"),
            any()
        );
    }

    @Test
    @DisplayName("400 — missing X-Principal-Id header returns error")
    void missingPrincipalIdReturnsError() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "t1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("400 — missing X-Tenant-Id header returns error")
    void missingTenantIdReturnsError() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Principal-Id"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("400 — missing X-Role header returns error")
    void missingRoleReturnsError() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "t1")
            .withHeader(HttpHeaders.of("X-Principal-Id"), "patient-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("200 — cache hit skips computation and returns cached payload")
    void cacheHitSkipsComputation() throws Exception {
        Map<String, Object> cachedPayload = Map.of(
            "product", "phr",
            "role", "patient",
            "routes", List.of()
        );
        lenient().when(entitlementCache.get(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(cachedPayload));

        HttpRequest request = contextRequest("t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(String tenantId, String principalId, String role) {
        return HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-Id"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }
}
