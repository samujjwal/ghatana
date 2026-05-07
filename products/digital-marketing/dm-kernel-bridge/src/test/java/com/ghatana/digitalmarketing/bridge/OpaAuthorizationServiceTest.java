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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpaAuthorizationService}.
 */
@DisplayName("OpaAuthorizationService")
class OpaAuthorizationServiceTest extends EventloopTestBase {

    private TestPolicyAsCodeEngine policyEngine;
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
        policyEngine = new TestPolicyAsCodeEngine();
        authService = new OpaAuthorizationService(policyEngine);
    }

    @Test
    @DisplayName("returns true when OPA policy allows the request")
    void allowsWhenPolicyAllows() {
        BridgeContext ctx = buildContext("acme", "user-42");
        PolicyEvalResult allowResult = PolicyEvalResult.allow("dmos/authz");
        policyEngine.setEvalResult(allowResult);

        Boolean result = runPromise(() -> authService.isAuthorized(ctx, "campaigns/123", "read"));

        assertThat(result).isTrue();
        assertThat(policyEngine.getLastTenantId()).isEqualTo("acme");
        assertThat(policyEngine.getLastPolicyId()).isEqualTo("dmos/authz");
    }

    @Test
    @DisplayName("returns false when OPA policy denies the request")
    void deniesWhenPolicyDenies() {
        BridgeContext ctx = buildContext("acme", "user-42");
        PolicyEvalResult denyResult = PolicyEvalResult.deny("dmos/authz",
            List.of("principal has no write permission"), 80);
        policyEngine.setEvalResult(denyResult);

        Boolean result = runPromise(() -> authService.isAuthorized(ctx, "campaigns/123", "write"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("passes tenantId, principalId, resource, and action to OPA input")
    void passesPolicyInputCorrectly() {
        BridgeContext ctx = buildContext("tenant-xyz", "svc-worker");
        PolicyEvalResult allowResult = PolicyEvalResult.allow("dmos/authz");
        policyEngine.setEvalResult(allowResult);

        Boolean result = runPromise(() -> authService.isAuthorized(ctx, "privacy/dsar", "write"));

        assertThat(result).isTrue();
        assertThat(policyEngine.getLastInput()).containsEntry("tenantId", "tenant-xyz");
        assertThat(policyEngine.getLastInput()).containsEntry("principalId", "svc-worker");
        assertThat(policyEngine.getLastInput()).containsEntry("resource", "privacy/dsar");
        assertThat(policyEngine.getLastInput()).containsEntry("action", "write");
    }

    @Test
    @DisplayName("fails closed (propagates exception) when policy engine throws")
    void failsClosedOnEngineException() {
        BridgeContext ctx = buildContext("acme", "user-99");
        policyEngine.setFailure(new RuntimeException("OPA unreachable"));

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

    private static final class TestPolicyAsCodeEngine implements PolicyAsCodeEngine {
        private PolicyEvalResult evalResult = PolicyEvalResult.allow("default");
        private RuntimeException failure;
        private String lastTenantId;
        private String lastPolicyId;
        private Map<String, Object> lastInput;

        void setEvalResult(PolicyEvalResult result) {
            this.evalResult = result;
            this.failure = null;
        }

        void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        String getLastTenantId() {
            return lastTenantId;
        }

        String getLastPolicyId() {
            return lastPolicyId;
        }

        Map<String, Object> getLastInput() {
            return lastInput;
        }

        @Override
        public Promise<PolicyEvalResult> evaluate(String tenantId, String policyId, Map<String, Object> input) {
            this.lastTenantId = tenantId;
            this.lastPolicyId = policyId;
            this.lastInput = input;
            if (failure != null) {
                return Promise.ofException(failure);
            }
            return Promise.of(evalResult);
        }
    }
}
