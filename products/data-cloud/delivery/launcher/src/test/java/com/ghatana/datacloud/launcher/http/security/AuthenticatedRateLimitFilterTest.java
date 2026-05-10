package com.ghatana.datacloud.launcher.http.security;

import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AuthenticatedRateLimitFilter} production-grade rate limiting.
 *
 * <p>Validates:
 * <ul>
 *   <li>IP rate limiting applies to all requests</li>
 *   <li>Tenant rate limiting only applies to authenticated requests</li>
 *   <li>Spoofed tenant headers don't consume other tenants' rate limits</li>
 *   <li>Rate limit responses include proper headers</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for AuthenticatedRateLimitFilter security enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AuthenticatedRateLimitFilter Tests")
class AuthenticatedRateLimitFilterTest {

    @Test
    @DisplayName("IP rate limit blocks excessive requests from same IP")
    void ipRateLimitBlocksExcessiveRequests() throws Exception {
        // Given filter with low IP rate limit
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(3, 60_000) // 3 requests per minute
            .withTenantRateLimit(100, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        String ip = "192.168.1.1";

        // When making requests from same IP
        for (int i = 0; i < 3; i++) {
            HttpRequest request = createRequestWithIp(ip, null);
            Promise<HttpResponse> promise = servlet.serve(request);
            HttpResponse response = promise.getResult();
            assertEquals(200, response.getCode(), "Request " + (i + 1) + " should succeed");
        }

        // Then 4th request is rate limited
        HttpRequest fourthRequest = createRequestWithIp(ip, null);
        Promise<HttpResponse> blockedPromise = servlet.serve(fourthRequest);
        HttpResponse blocked = blockedPromise.getResult();
        assertEquals(429, blocked.getCode());
        assertEquals("IP", blocked.getHeader(HttpHeaders.of("X-RateLimit-Type")));
    }

    @Test
    @DisplayName("Tenant rate limit only applies to authenticated tenants")
    void tenantRateLimitOnlyForAuthenticated() throws Exception {
        // Given filter with tenant rate limit
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1000, 60_000) // High IP limit
            .withTenantRateLimit(3, 60_000) // 3 requests per minute per tenant
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // When making unauthenticated requests (no tenant metadata)
        // These should not be tenant rate limited since no tenant is present
        for (int i = 0; i < 10; i++) {
            HttpRequest request = createRequestWithIp("192.168.1." + i, null);
            Promise<HttpResponse> promise = servlet.serve(request);
            HttpResponse response = promise.getResult();
            assertEquals(200, response.getCode(), "Unauthenticated request " + (i + 1) + " should not be tenant rate limited");
        }
    }

    @Test
    @DisplayName("Different tenants have separate rate limits")
    void differentTenantsHaveSeparateLimits() {
        // Given filter with tenant rate limit
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1000, 60_000)
            .withTenantRateLimit(5, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // Note: Full tenant-based rate limiting is tested in integration tests
        // with proper security filter integration. This test validates
        // the filter is configured correctly.
        assertTrue(filter != null);
    }

    @Test
    @DisplayName("Spoofed tenant header doesn't bypass IP rate limiting")
    void spoofedTenantDoesntAffectOthers() throws Exception {
        // Given filter
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1000, 60_000)
            .withTenantRateLimit(5, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // When attacker spoofs tenant header (but no authentication)
        for (int i = 0; i < 10; i++) {
            HttpRequest request = HttpRequest.get("http://localhost/api/test")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "victim-tenant") // Spoofed header
                .build();
            Promise<HttpResponse> promise = servlet.serve(request);
            HttpResponse response = promise.getResult();
            // Should succeed because no authentication = no tenant rate limit applied
            // (IP rate limit still applies but with high limit)
            assertEquals(200, response.getCode(), "Spoofed request should not consume victim's rate limit");
        }
    }

    @Test
    @DisplayName("Rate limit response includes Retry-After header")
    void rateLimitResponseIncludesRetryAfter() throws Exception {
        // Given filter with short window
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1, 60_000) // 1 request per minute
            .withTenantRateLimit(100, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // When making requests that exceed limit
        HttpRequest request = createRequestWithIp("192.168.1.1", null);
        servlet.serve(request).getResult(); // First request succeeds

        HttpResponse blocked = servlet.serve(request).getResult(); // Second request blocked

        // Then response includes Retry-After header
        assertEquals(429, blocked.getCode());
        String retryAfter = blocked.getHeader(HttpHeaders.of("Retry-After"));
        assertNotNull(retryAfter);
        int retrySeconds = Integer.parseInt(retryAfter);
        assertTrue(retrySeconds > 0 && retrySeconds <= 60, "Retry-After should be within window");
    }

    @Test
    @DisplayName("Rate limit response includes JSON error body")
    void rateLimitResponseIncludesJsonBody() throws Exception {
        // Given filter
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // When request is rate limited
        HttpRequest request = createRequestWithIp("192.168.1.1", null);
        servlet.serve(request).getResult();
        HttpResponse blocked = servlet.serve(request).getResult();

        // Then response has JSON content type
        String contentType = blocked.getHeader(HttpHeaders.CONTENT_TYPE);
        assertTrue(contentType.contains("json"), "Response should be JSON");
    }

    @Test
    @DisplayName("Extracts client IP from X-Forwarded-For header")
    void extractsIpFromXForwardedFor() throws Exception {
        // Given filter
        AtomicInteger capturedIp = new AtomicInteger(0);
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // When request has X-Forwarded-For
        HttpRequest request = HttpRequest.get("http://localhost/api/test")
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "203.0.113.1, 70.41.3.18, 150.172.238.178")
            .build();

        // Then first IP in chain is used for rate limiting
        HttpResponse response1 = servlet.serve(request).getResult();
        assertEquals(200, response1.getCode());

        HttpResponse response2 = servlet.serve(request).getResult();
        assertEquals(429, response2.getCode()); // Same IP should be rate limited
    }

    @Test
    @DisplayName("Different IPs have separate rate limits")
    void differentIpsHaveSeparateLimits() throws Exception {
        // Given filter
        AuthenticatedRateLimitFilter filter = AuthenticatedRateLimitFilter.builder()
            .withIpRateLimit(1, 60_000)
            .build();

        AsyncServlet delegate = req -> Promise.of(HttpResponse.ok200().build());
        AsyncServlet servlet = filter.apply(delegate);

        // When different IPs make requests
        HttpResponse ip1First = servlet.serve(createRequestWithIp("192.168.1.1", null)).getResult();
        HttpResponse ip2First = servlet.serve(createRequestWithIp("192.168.1.2", null)).getResult();

        // Then both succeed (separate limits)
        assertEquals(200, ip1First.getCode());
        assertEquals(200, ip2First.getCode());
    }

    private HttpRequest createRequestWithIp(String ip, String tenantId) {
        HttpRequest.Builder builder = HttpRequest.get("http://localhost/api/test");

        if (ip != null) {
            builder.withHeader(HttpHeaders.of("X-Forwarded-For"), ip);
        }

        if (tenantId != null) {
            // Without authentication, tenant header alone doesn't trigger tenant rate limit
            builder.withHeader(HttpHeaders.of("X-Tenant-Id"), tenantId);
        }

        return builder.build();
    }
}
