package com.ghatana.appplatform.rules;

import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JurisdictionPolicyRouter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for jurisdiction-based OPA policy routing
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JurisdictionPolicyRouter — Unit Tests")
class JurisdictionPolicyRouterTest {

    @Mock
    private RuleCacheService cacheService;

    private JurisdictionPolicyRouter router;

    private static final OpaEvaluationService.OpaResult ALLOW_RESULT =
            new OpaEvaluationService.OpaResult(true, Map.of("allow", true), "eu/authz/allow");

    @BeforeEach
    void setUp() {
        router = new JurisdictionPolicyRouter(cacheService);
    }

    @Test
    @DisplayName("route_knownTenant — resolves to jurisdiction-specific OPA path")
    void routeKnownTenant() throws Exception {
        router.registerTenantJurisdiction("tenant-acme", "EU");
        when(cacheService.getOrEvaluate(eq("eu/authz/allow"), anyMap()))
                .thenReturn(Promise.of(ALLOW_RESULT));

        Map<String, Object> input = Map.of("userId", "u-1");
        OpaEvaluationService.OpaResult result =
                router.routeAndEvaluate("tenant-acme", "authz/allow", input).getResult();

        assertThat(result.allow()).isTrue();
        verify(cacheService).getOrEvaluate(eq("eu/authz/allow"), anyMap());
    }

    @Test
    @DisplayName("route_unknownTenant — falls back to DEFAULT (global) bundle")
    void routeUnknownTenantFallsBack() throws Exception {
        OpaEvaluationService.OpaResult globalResult =
                new OpaEvaluationService.OpaResult(false, Map.of("allow", false), "global/authz/allow");
        when(cacheService.getOrEvaluate(eq("global/authz/allow"), anyMap()))
                .thenReturn(Promise.of(globalResult));

        OpaEvaluationService.OpaResult result =
                router.routeAndEvaluate("unknown-tenant", "authz/allow", Map.of()).getResult();

        assertThat(result.allow()).isFalse();
        verify(cacheService).getOrEvaluate(eq("global/authz/allow"), anyMap());
    }

    @Test
    @DisplayName("registerTenantJurisdiction — changes routing for tenant immediately")
    void registerTenantUpdatesRouting() throws Exception {
        router.registerTenantJurisdiction("tenant-remap", "US");
        OpaEvaluationService.OpaResult usResult =
                new OpaEvaluationService.OpaResult(true, Map.of(), "us/authz/allow");
        when(cacheService.getOrEvaluate(eq("us/authz/allow"), anyMap()))
                .thenReturn(Promise.of(usResult));

        OpaEvaluationService.OpaResult result =
                router.routeAndEvaluate("tenant-remap", "authz/allow", Map.of()).getResult();

        assertThat(result.policyPath()).isEqualTo("us/authz/allow");
    }

    @Test
    @DisplayName("registerJurisdictionBundle — custom bundle prefix is honoured")
    void customBundlePrefixHonoured() throws Exception {
        router.registerTenantJurisdiction("tenant-np", "NP");
        router.registerJurisdictionBundle("NP", "nepal");
        OpaEvaluationService.OpaResult npResult =
                new OpaEvaluationService.OpaResult(true, Map.of(), "nepal/trade/allow");
        when(cacheService.getOrEvaluate(eq("nepal/trade/allow"), anyMap()))
                .thenReturn(Promise.of(npResult));

        OpaEvaluationService.OpaResult result =
                router.routeAndEvaluate("tenant-np", "trade/allow", Map.of()).getResult();

        assertThat(result.policyPath()).isEqualTo("nepal/trade/allow");
    }

    @Test
    @DisplayName("opaResult_record — stores fields correctly")
    void opaResultRecord() {
        OpaEvaluationService.OpaResult result =
                new OpaEvaluationService.OpaResult(true, Map.of("reason", "ok"), "authz/allow");

        assertThat(result.allow()).isTrue();
        assertThat(result.result()).containsEntry("reason", "ok");
        assertThat(result.policyPath()).isEqualTo("authz/allow");
    }
}
