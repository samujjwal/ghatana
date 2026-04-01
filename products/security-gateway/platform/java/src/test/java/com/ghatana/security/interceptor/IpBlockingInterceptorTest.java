package com.ghatana.security.interceptor;

import com.ghatana.security.audit.SecurityAuditLogger;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Tests IP blocking and auto-ban functionality
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("IpBlockingInterceptor Tests")
class IpBlockingInterceptorTest extends EventloopTestBase {

    private final SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);
    private final SecurityInterceptor.NextHandler passThrough =
            req -> io.activej.promise.Promise.of(HttpResponse.ok200().build());

    @Test
    @DisplayName("should block permanently listed IPs")
    void shouldBlockPermanentlyListedIps() {
        IpBlockingInterceptor blocker = new IpBlockingInterceptor(
                auditLogger, Set.of("10.0.0.99"), 5, Duration.ofMinutes(1), Duration.ofHours(1), true
        );

        HttpRequest request = requestFromIp("10.0.0.99");
        HttpResponse response = runPromise(() -> blocker.intercept(request, passThrough));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("should allow non-blocked IPs")
    void shouldAllowNonBlockedIps() {
        IpBlockingInterceptor blocker = new IpBlockingInterceptor(
                auditLogger, Set.of("10.0.0.99"), 5, Duration.ofMinutes(1), Duration.ofHours(1), true
        );

        HttpRequest request = requestFromIp("10.0.0.1");
        HttpResponse response = runPromise(() -> blocker.intercept(request, passThrough));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should auto-ban IP after exceeding violation threshold")
    void shouldAutoBanAfterThreshold() {
        IpBlockingInterceptor blocker = new IpBlockingInterceptor(
                auditLogger, Set.of(), 3, Duration.ofMinutes(5), Duration.ofHours(1), true
        );

        // Record 3 violations from the same IP
        blocker.recordViolation("10.0.0.50");
        blocker.recordViolation("10.0.0.50");
        blocker.recordViolation("10.0.0.50");

        HttpRequest request = requestFromIp("10.0.0.50");
        HttpResponse response = runPromise(() -> blocker.intercept(request, passThrough));
        assertThat(response.getCode()).isEqualTo(403);
        assertThat(blocker.getAutoBannedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should not auto-ban below threshold")
    void shouldNotBanBelowThreshold() {
        IpBlockingInterceptor blocker = new IpBlockingInterceptor(
                auditLogger, Set.of(), 3, Duration.ofMinutes(5), Duration.ofHours(1), true
        );

        blocker.recordViolation("10.0.0.50");
        blocker.recordViolation("10.0.0.50");

        HttpRequest request = requestFromIp("10.0.0.50");
        HttpResponse response = runPromise(() -> blocker.intercept(request, passThrough));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should unban IP manually")
    void shouldUnbanIpManually() {
        IpBlockingInterceptor blocker = new IpBlockingInterceptor(
                auditLogger, Set.of(), 2, Duration.ofMinutes(5), Duration.ofHours(1), true
        );

        blocker.recordViolation("10.0.0.50");
        blocker.recordViolation("10.0.0.50");
        assertThat(blocker.getAutoBannedCount()).isEqualTo(1);

        blocker.unbanIp("10.0.0.50");
        assertThat(blocker.getAutoBannedCount()).isZero();

        HttpRequest request = requestFromIp("10.0.0.50");
        HttpResponse response = runPromise(() -> blocker.intercept(request, passThrough));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should pass through when disabled")
    void shouldPassThroughWhenDisabled() {
        IpBlockingInterceptor blocker = new IpBlockingInterceptor(
                auditLogger, Set.of("10.0.0.99"), 3, Duration.ofMinutes(1), Duration.ofHours(1), false
        );

        HttpRequest request = requestFromIp("10.0.0.99");
        HttpResponse response = runPromise(() -> blocker.intercept(request, passThrough));
        assertThat(response.getCode()).isEqualTo(200);
    }

    private static HttpRequest requestFromIp(String ip) {
        return HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/resource")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), ip)
                .build();
    }
}
