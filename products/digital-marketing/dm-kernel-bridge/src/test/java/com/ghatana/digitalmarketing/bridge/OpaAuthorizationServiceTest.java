package com.ghatana.digitalmarketing.bridge;

import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpaAuthorizationService}.
 */
@DisplayName("OpaAuthorizationService")
@ExtendWith(MockitoExtension.class)
class OpaAuthorizationServiceTest extends EventloopTestBase {

    @Mock
    private PolicyAsCodeEngine policyEngine;

    private OpaAuthorizationService authService;

    private BridgeContext buildContext(String tenantId, String principalId) {
        return BridgeContext.builder()
            .tenantId(tenantId)
            .principalId(principalId)
            .correlationId("corr-test-001")
            .build();
    }

    @BeforeEach
    void setUp() {
        authService = new OpaAuthorizationService(policyEngine);
    }

    @Test
    @DisplayName("returns true when OPA policy allows the request")
    void allowsWhenPolicyAllows() {
        BridgeContext ctx = buildContext("acme", "user-42");
        PolicyEvalResult allowResult = PolicyEvalResult.allow("dmos/authz");
        when(policyEngine.evaluate(eq("acme"), eq("dmos/authz"), any()))
            .thenReturn(Promise.of(allowResult));

        Boolean result = runPromise(() -> authService.isAuthorized(ctx, "campaigns/123", "read"));

        assertThat(result).isTrue();
        verify(policyEngine).evaluate(eq("acme"), eq("dmos/authz"), any());
    }

    @Test
    @DisplayName("returns false when OPA policy denies the request")
    void deniesWhenPolicyDenies() {
        BridgeContext ctx = buildContext("acme", "user-42");
        PolicyEvalResult denyResult = PolicyEvalResult.deny("dmos/authz",
            List.of("principal has no write permission"), 80);
        when(policyEngine.evaluate(eq("acme"), eq("dmos/authz"), any()))
            .thenReturn(Promise.of(denyResult));

        Boolean result = runPromise(() -> authService.isAuthorized(ctx, "campaigns/123", "write"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("passes tenantId, principalId, resource, and action to OPA input")
    void passesPolicyInputCorrectly() {
        BridgeContext ctx = buildContext("tenant-xyz", "svc-worker");
        when(policyEngine.evaluate(eq("tenant-xyz"), eq("dmos/authz"), any()))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> input = (Map<String, Object>) inv.getArgument(2);
                assertThat(input).containsEntry("tenantId", "tenant-xyz");
                assertThat(input).containsEntry("principalId", "svc-worker");
                assertThat(input).containsEntry("resource", "privacy/dsar");
                assertThat(input).containsEntry("action", "write");
                return Promise.of(PolicyEvalResult.allow("dmos/authz"));
            });

        Boolean result = runPromise(() -> authService.isAuthorized(ctx, "privacy/dsar", "write"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("fails closed (propagates exception) when policy engine throws")
    void failsClosedOnEngineException() {
        BridgeContext ctx = buildContext("acme", "user-99");
        when(policyEngine.evaluate(any(), any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("OPA unreachable")));

        // The promise should fail — callers treat failure as denied via circuit breaker
        assertThatThrownBy(() -> runPromise(() -> authService.isAuthorized(ctx, "campaigns/1", "write")))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("OPA unreachable");
    }

    @Test
    @DisplayName("rejects null context")
    void rejectsNullContext() {
        assertThatNullPointerException()
            .isThrownBy(() -> authService.isAuthorized(null, "resource", "read"));
    }

    @Test
    @DisplayName("rejects null resource")
    void rejectsNullResource() {
        BridgeContext ctx = buildContext("acme", "user-1");
        assertThatNullPointerException()
            .isThrownBy(() -> authService.isAuthorized(ctx, null, "read"));
    }

    @Test
    @DisplayName("rejects null action")
    void rejectsNullAction() {
        BridgeContext ctx = buildContext("acme", "user-1");
        assertThatNullPointerException()
            .isThrownBy(() -> authService.isAuthorized(ctx, "campaigns/1", null));
    }

    @Test
    @DisplayName("constructor rejects null policyEngine")
    void constructorRejectsNullPolicyEngine() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OpaAuthorizationService(null));
    }
}
