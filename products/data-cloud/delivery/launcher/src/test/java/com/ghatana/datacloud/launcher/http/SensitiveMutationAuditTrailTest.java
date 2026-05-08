/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-SEC-003 — Audit evidence on sensitive mutations.
 *
 * <p>Verifies that durable audit events containing tenant, actor, trace id,
 * action, and result are emitted for all SENSITIVE and CRITICAL mutations.
 * Mutations that are denied must also produce an audit trail (fail-closed
 * auditability).
 *
 * @doc.type class
 * @doc.purpose DC-SEC-003 audit evidence on sensitive and critical mutations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-SEC-003 — Sensitive mutation audit trail")
class SensitiveMutationAuditTrailTest extends EventloopTestBase {

    private static final String VALID_API_KEY  = "sk-sec003-valid";
    private static final String TENANT         = "audit-trail-tenant";
    private static final String ACTOR          = "svc-audit-trail";
    private static final String SENSITIVE_PATH = "/api/v1/voice/intent";
    private static final String CRITICAL_PATH  = "/api/v1/governance/policies/test";
    private static final String INTERNAL_PATH  = "/api/v1/collections/sec003-check";

    private ApiKeyResolver apiKeyResolver;
    private PolicyEngine   policyEngine;
    private AuditService   auditService;

    private static final AsyncServlet OK_DELEGATE =
            request -> Promise.of(HttpResponse.ok200().build());

    @BeforeEach
    void setUp() {
        apiKeyResolver = mock(ApiKeyResolver.class);
        policyEngine   = mock(PolicyEngine.class);
        auditService   = mock(AuditService.class);

        Principal principal = new Principal(ACTOR, List.of("admin"), TENANT);
        when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of((Void) null));
        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
    }

    private DataCloudSecurityFilter.Builder base() {
        return DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(true);
    }

    private HttpRequest requestTo(String path) {
        return HttpRequest.post("http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    // =========================================================================
    // SENSITIVE path audit
    // =========================================================================

    @Nested
    @DisplayName("SENSITIVE path mutations emit audit event")
    class SensitiveMutations {

        @Test
        @DisplayName("successful SENSITIVE mutation produces audit event with tenant and actor")
        void sensitiveMutation_emitsAuditWithTenantAndActor() {
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(SENSITIVE_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getTenantId()).isEqualTo(TENANT);
            assertThat(event.getPrincipal()).isEqualTo(ACTOR);
        }

        @Test
        @DisplayName("successful SENSITIVE mutation audit event records success=true")
        void sensitiveMutation_auditMarkedSuccess() {
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(SENSITIVE_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().getSuccess()).isTrue();
        }

        @Test
        @DisplayName("SENSITIVE mutation audit event includes resource action")
        void sensitiveMutation_auditIncludesResourceAndAction() {
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(SENSITIVE_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getResourceType()).isNotBlank();
            assertThat(event.getResourceId()).contains(SENSITIVE_PATH);
        }
    }

    // =========================================================================
    // CRITICAL path audit
    // =========================================================================

    @Nested
    @DisplayName("CRITICAL path mutations emit audit event")
    class CriticalMutations {

        @Test
        @DisplayName("allowed CRITICAL mutation produces audit event with correct tenant and actor")
        void criticalMutation_allowed_emitsAuditWithTenantAndActor() {
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(CRITICAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getTenantId()).isEqualTo(TENANT);
            assertThat(event.getPrincipal()).isEqualTo(ACTOR);
        }

        @Test
        @DisplayName("denied CRITICAL mutation still produces an audit event (fail-closed auditability)")
        void criticalMutation_policyDeny_stillEmitsAudit() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(CRITICAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            // Audit must be emitted even when the request is denied — this is required
            // to provide a forensic trail for blocked access attempts.
            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("denied CRITICAL mutation audit event records success=false")
        void criticalMutation_policyDeny_auditMarkedFailure() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(CRITICAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().getSuccess()).isFalse();
        }

        @Test
        @DisplayName("CRITICAL mutation audit event includes sensitivity level in details")
        void criticalMutation_auditIncludesSensitivityDetail() {
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(CRITICAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());

            // Details map must carry the sensitivity level for incident classification
            assertThat(captor.getValue().getDetails())
                    .containsKey("sensitivity");
        }
    }

    // =========================================================================
    // INTERNAL paths — no audit
    // =========================================================================

    @Nested
    @DisplayName("INTERNAL paths do not produce audit events")
    class InternalPaths {

        @Test
        @DisplayName("successful INTERNAL mutation does not emit an audit event")
        void internalPath_noAuditEmitted() {
            AsyncServlet secured = base().build().apply(OK_DELEGATE);
            HttpRequest req = requestTo(INTERNAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService, never()).record(any(AuditEvent.class));
        }
    }
}
