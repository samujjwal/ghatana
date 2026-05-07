/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.server.governance.KillSwitchAuditChain;
import com.ghatana.aep.server.governance.StepUpAuthenticationGate;
import com.ghatana.aep.compliance.AepSoc2ControlFramework;
import com.ghatana.platform.incident.InMemoryGracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryKillSwitchService;
import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.security.analytics.DefaultEgressMonitor;
import com.ghatana.platform.security.analytics.RegexPromptInjectionDetector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GovernanceController} — kill-switch MFA gate and audit chain.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Kill-switch activation with valid MFA code succeeds and records audit entry</li>
 *   <li>Kill-switch activation with invalid MFA code returns 403 and records failed attempt</li>
 *   <li>Kill-switch activation when step-up gate is absent proceeds without MFA check</li>
 *   <li>Audit chain is NOT invoked for valid non-MFA paths (no gate wired)</li>
 *   <li>Kill-switch deactivation records a deactivation audit entry</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Kill-switch MFA gate and audit chain unit tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GovernanceController – kill-switch MFA and audit chain")
@ExtendWith(MockitoExtension.class)
class GovernanceControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private StepUpAuthenticationGate stepUpGate;

    @Mock
    private KillSwitchAuditChain auditChain;

    private GovernanceController controller;

    @BeforeEach
    void setUp() {
        controller = new GovernanceController(
            new InMemoryKillSwitchService(),
            new InMemoryGracefulDegradationManager(),
            new InMemoryPolicyEngine(),
            new DefaultEgressMonitor(),
            new RegexPromptInjectionDetector(),
            body -> {
                try {
                    String json = MAPPER.writeValueAsString(body);
                    return HttpResponse.ok200()
                        .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                        .build();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            null,
            new AepSoc2ControlFramework(),
            stepUpGate,
            auditChain);
    }

    // ==================== MFA gate: valid code ====================

    @Nested
    @DisplayName("kill-switch activation with step-up gate wired")
    class KillSwitchMfaTests {

        @Test
        @DisplayName("valid MFA code activates kill-switch and records audit entry")
        void validMfaActivatesAndAudits() throws Exception {
            when(stepUpGate.verify("admin-user", "tenant-mfa", "123456"))
                .thenReturn(Promise.of(true));
            when(auditChain.recordActivation(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of("audit-001"));

            HttpResponse resp = runPromise(() -> controller.handleActivateKillSwitch(
                postRequest(Map.of(
                    "tenantId", "tenant-mfa",
                    "userId", "admin-user",
                    "mfaCode", "123456",
                    "reason", "incident-response",
                    "incidentId", "INC-99",
                    "role", "admin"
                ))));

            assertThat(resp.getCode()).isEqualTo(200);
            Map<?, ?> body = parseBody(resp);
            assertThat(body.get("activated")).isEqualTo(true);
            assertThat(body.get("auditId")).isEqualTo("audit-001");

            verify(stepUpGate).verify("admin-user", "tenant-mfa", "123456");
            verify(auditChain).recordActivation("tenant-mfa", "admin-user", "incident-response", "INC-99", "admin");
        }

        @Test
        @DisplayName("invalid MFA code returns 403 and records failed step-up attempt")
        void invalidMfaReturnsForbiddenAndAudits() throws Exception {
            when(stepUpGate.verify("admin-user", "tenant-mfa", "999999"))
                .thenReturn(Promise.of(false));
            when(auditChain.recordFailedStepUp("tenant-mfa", "admin-user", "kill-switch activation denied: MFA failed"))
                .thenReturn(Promise.of("audit-fail-001"));

            HttpResponse resp = runPromise(() -> controller.handleActivateKillSwitch(
                postRequest(Map.of(
                    "tenantId", "tenant-mfa",
                    "userId", "admin-user",
                    "mfaCode", "999999",
                    "reason", "attempted",
                    "incidentId", "INC-100",
                    "role", "admin"
                ))));

            assertThat(resp.getCode()).isEqualTo(403);
            String bodyStr = resp.getBody().getString(StandardCharsets.UTF_8);
            assertThat(bodyStr).contains("MFA verification failed");

            verify(stepUpGate).verify("admin-user", "tenant-mfa", "999999");
            verify(auditChain).recordFailedStepUp("tenant-mfa", "admin-user", "kill-switch activation denied: MFA failed");
        }

        @Test
        @DisplayName("kill-switch activation without step-up gate does not invoke audit chain")
        void noGateAllowsDirectActivationWithoutAudit() throws Exception {
            // Build a controller with NO step-up gate and NO audit chain
            GovernanceController noGateController = new GovernanceController(
                new InMemoryKillSwitchService(),
                new InMemoryGracefulDegradationManager(),
                new InMemoryPolicyEngine(),
                new DefaultEgressMonitor(),
                new RegexPromptInjectionDetector(),
                body -> {
                    try {
                        String json = MAPPER.writeValueAsString(body);
                        return HttpResponse.ok200()
                            .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                            .build();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                null,
                new AepSoc2ControlFramework(),
                null,   // no step-up gate
                null);  // no audit chain

            HttpResponse resp = runPromise(() -> noGateController.handleActivateKillSwitch(
                postRequest(Map.of(
                    "tenantId", "tenant-no-gate",
                    "userId", "operator",
                    "reason", "maintenance",
                    "incidentId", "MNT-1",
                    "role", "admin"
                ))));

            assertThat(resp.getCode()).isEqualTo(200);
            Map<?, ?> body = parseBody(resp);
            assertThat(body.get("activated")).isEqualTo(true);

            // Neither the shared step-up gate mock nor audit chain mock should be called
            verify(stepUpGate, never()).verify(anyString(), anyString(), anyString());
            verify(auditChain, never()).recordActivation(anyString(), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("kill-switch activation missing tenantId returns 400")
        void missingTenantIdReturns400() throws Exception {
            HttpResponse resp = runPromise(() -> controller.handleActivateKillSwitch(
                postRequest(Map.of("userId", "admin", "mfaCode", "123456"))));

            assertThat(resp.getCode()).isEqualTo(400);
        }
    }

    // ==================== Deactivation audit chain ====================

    @Nested
    @DisplayName("kill-switch deactivation audit")
    class KillSwitchDeactivationAuditTests {

        @Test
        @DisplayName("deactivation records audit entry when chain is configured")
        void deactivationRecordsAuditEntry() throws Exception {
            // First activate, then deactivate to exercise the deactivation path
            when(stepUpGate.verify(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(true));
            when(auditChain.recordActivation(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of("audit-a1"));
            when(auditChain.recordDeactivation(anyString(), anyString(), anyString(), any()))
                .thenReturn(Promise.of("audit-d1"));

            // Activate first
            runPromise(() -> controller.handleActivateKillSwitch(
                postRequest(Map.of(
                    "tenantId", "tenant-deact",
                    "userId", "admin",
                    "mfaCode", "111111",
                    "reason", "test",
                    "incidentId", "INC-D",
                    "role", "admin"
                ))));

            // Deactivate
            HttpResponse deactResp = runPromise(() -> controller.handleDeactivateKillSwitch(
                postRequest(Map.of(
                    "tenantId", "tenant-deact",
                    "userId", "admin",
                    "mfaCode", "222222",
                    "reason", "resolved",
                    "role", "admin"
                ))));

            assertThat(deactResp.getCode()).isEqualTo(200);
            Map<?, ?> body = parseBody(deactResp);
            assertThat(body.get("deactivated")).isEqualTo(true);

            verify(auditChain).recordDeactivation("tenant-deact", "admin", "resolved", "admin");
        }
    }

    // ==================== Helpers ====================

    private HttpRequest postRequest(Map<String, Object> payload) throws Exception {
        String json = MAPPER.writeValueAsString(payload);
        return HttpRequest.post("http://localhost/api/v1/governance/kill-switch/activate")
            .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
            .build();
    }

    private Map<?, ?> parseBody(HttpResponse response) throws Exception {
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(body, Map.class);
    }
}
