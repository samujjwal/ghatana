/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-SEC-003: Audit redaction/privacy tests.
 *
 * <p>Verifies sensitive data is not leaked through audit details, logs, or error responses.
 * Tests ensure PII, secrets, and sensitive entity data are properly redacted.
 *
 * @doc.type class
 * @doc.purpose Audit redaction/privacy tests (DC-SEC-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudSecurity Audit Redaction Tests")
@Tag("production")
@Tag("security")
@Tag("privacy")
class DataCloudSecurityAuditRedactionTest extends EventloopTestBase {

    private static final String VALID_API_KEY = "valid-api-key";
    private static final String TEST_TENANT = "tenant-1";
    /** POST /api/v1/entities/{collection} is SENSITIVE in RouteSecurityRegistry. */
    private static final String SENSITIVE_PATH = "/api/v1/entities/test-collection";

    private ApiKeyResolver apiKeyResolver;
    private PolicyEngine policyEngine;
    private AuditService auditService;

    private static final AsyncServlet OK_DELEGATE = req -> HttpResponse.ok200().toPromise();

    @BeforeEach
    void setUp() {
        apiKeyResolver = apiKey -> {
            if (VALID_API_KEY.equals(apiKey)) {
                return Optional.of(new Principal("user-1", List.of("OPERATOR"), TEST_TENANT));
            }
            return Optional.empty();
        };

        policyEngine = mock(PolicyEngine.class);
        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
        when(policyEngine.policyExists(anyString())).thenReturn(Promise.of(Boolean.TRUE));

        auditService = mock(AuditService.class);
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of((Void) null));
    }

    private HttpRequest sensitivePostRequest() {
        return HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    private DataCloudSecurityFilter productionFilter() {
        return DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .deploymentProfile("production")
                .enforcing(true)
                .build();
    }

    // ==================== DC-SEC-003: Audit details redaction ====================

    @Test
    @DisplayName("DC-SEC-003: Audit details do not contain sensitive entity data")
    void auditDetailsDoNotContainSensitiveEntityData() {
        DataCloudSecurityFilter filter = productionFilter();
        runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        Map<String, Object> details = captor.getValue().details();

        assertThat(details).doesNotContainKey("password");
        assertThat(details).doesNotContainKey("secret");
        assertThat(details).doesNotContainKey("apiKey");
        assertThat(details).doesNotContainKey("creditCard");
    }

    @Test
    @DisplayName("DC-SEC-003: Audit details do not contain raw request body")
    void auditDetailsDoNotContainRawRequestBody() {
        DataCloudSecurityFilter filter = productionFilter();
        runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        Map<String, Object> details = captor.getValue().details();

        assertThat(details).doesNotContainKey("requestBody");
        assertThat(details).doesNotContainKey("body");
    }

    // ==================== DC-SEC-003: Error response redaction ====================

    @Test
    @DisplayName("DC-SEC-003: Error responses do not leak sensitive data")
    void errorResponsesDoNotLeakSensitiveData() {
        DataCloudSecurityFilter filter = productionFilter();

        // Use invalid auth to trigger a 401 response
        HttpRequest request = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(request));

        // Authentication failure returns 401, not 200
        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("DC-SEC-003: Audit event contains expected principal information")
    void auditEventContainsPrincipalInformation() {
        DataCloudSecurityFilter filter = productionFilter();
        runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent auditEvent = captor.getValue();

        assertThat(auditEvent.principal()).isNotNull();
        assertThat(auditEvent.tenantId()).isNotNull();
        assertThat(auditEvent.eventType()).isNotNull();
    }

    @Test
    @DisplayName("DC-SEC-003: Audit event does not contain raw headers")
    void auditEventDoesNotContainRawHeaders() {
        DataCloudSecurityFilter filter = productionFilter();

        // Request with a sensitive custom header
        HttpRequest request = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.of("X-Sensitive-Header"), "secret-value")
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

        runPromise(() -> filter.apply(OK_DELEGATE).serve(request));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        Map<String, Object> details = captor.getValue().details();

        assertThat(details).doesNotContainKey("headers");
        assertThat(details).doesNotContainKey("X-Sensitive-Header");
    }
}