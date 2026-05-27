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
import io.activej.promise.SettablePromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-SEC-002: Audit failure injection tests for SENSITIVE and CRITICAL routes.
 *
 * <p>Verifies fire-and-forget audit behavior: audit failures are logged but never
 * block the response path. Null audit service is blocked in production profiles for
 * SENSITIVE/CRITICAL routes.
 *
 * @doc.type class
 * @doc.purpose Audit failure injection tests (DC-SEC-002)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudSecurity Audit Failure Injection Tests")
@Tag("production")
@Tag("security")
@Tag("audit")
class DataCloudSecurityAuditFailureInjectionTest extends EventloopTestBase {

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
    }

    private HttpRequest sensitivePostRequest() {
        return HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    // ==================== DC-SEC-002: Audit sink unavailable ====================

    @Test
    @DisplayName("DC-SEC-002: Audit sink failure does not block SENSITIVE route (fire-and-forget)")
    void auditSinkUnavailable_doesNotBlockSensitiveRoute() {
        when(auditService.record(any(AuditEvent.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Audit sink unavailable")));

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .deploymentProfile("production")
                .enforcing(true)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        // Audit is fire-and-forget; failures are logged but do not block the response
        assertThat(response.getCode()).isEqualTo(200);
        verify(auditService).record(any(AuditEvent.class));
    }

    // ==================== DC-SEC-002: Audit record returns failed promise ====================

    @Test
    @DisplayName("DC-SEC-002: Async audit failure does not block SENSITIVE route")
    void auditRecordFails_doesNotBlockSensitiveRoute() {
        when(auditService.record(any(AuditEvent.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Audit record serialization failed")));

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .deploymentProfile("production")
                .enforcing(true)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        // Audit is fire-and-forget; async failures are caught and logged, not propagated
        assertThat(response.getCode()).isEqualTo(200);
        verify(auditService).record(any(AuditEvent.class));
    }

    // ==================== DC-SEC-002: Audit pending (never completes) ====================

    @Test
    @DisplayName("DC-SEC-002: Pending audit promise does not block SENSITIVE route")
    void auditPendingPromise_doesNotBlockSensitiveRoute() {
        // A promise that never resolves; fire-and-forget means it does not block the handler
        SettablePromise<Void> neverCompletes = new SettablePromise<>();
        when(auditService.record(any(AuditEvent.class))).thenReturn(neverCompletes);

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .deploymentProfile("production")
                .enforcing(true)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        // Response is returned immediately; audit was attempted but does not block
        assertThat(response.getCode()).isEqualTo(200);
        verify(auditService).record(any(AuditEvent.class));
    }

    // ==================== DC-SEC-002: Audit service missing ====================

    @Test
    @DisplayName("DC-SEC-002: Null audit service blocks SENSITIVE route in production profile")
    void nullAuditService_blocksSensitiveRouteInProduction() {
        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(null)   // deliberately missing
                .deploymentProfile("production")
                .enforcing(true)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(sensitivePostRequest()));

        // Audit service is required for SENSITIVE routes in production; block with 503
        assertThat(response.getCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("DC-SEC-002: PUBLIC route succeeds without audit service")
    void publicRoute_succeedsWithoutAuditService() {
        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(null)
                .deploymentProfile("production")
                .enforcing(true)
                .build();

        HttpRequest healthRequest = HttpRequest.get("http://localhost/health")
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(healthRequest));

        // PUBLIC routes bypass auth and audit checks entirely
        assertThat(response.getCode()).isEqualTo(200);
    }
}