/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.generated.GeneratedRouteRegistry;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.governance.route.AuthMode;
import com.ghatana.yappc.governance.route.PrivacyClassification;
import com.ghatana.yappc.governance.route.RouteEntry;
import io.activej.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Privacy contract tests for restricted/confidential routes and telemetry payloads.
 *
 * @doc.type test
 * @doc.purpose Verifies route privacy metadata and redaction before logs or serialized responses
 * @doc.layer api
 * @doc.pattern ContractTest
 */
@DisplayName("YAPPC privacy contracts")
class YappcPrivacyContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("confidential and restricted routes are authenticated and registry classifications match manifest")
    void confidentialAndRestrictedRoutesAreAuthenticatedAndRegisteredWithMatchingClassification() {
        RouteAuthorizationRegistry registry = new RouteAuthorizationRegistry(mock(YappcAuthorizationService.class));

        List<RouteEntry> sensitiveRoutes = GeneratedRouteRegistry.getManifest()
                .getRoutesForServer("yappc-services")
                .stream()
                .filter(route -> route.privacyClassification() == PrivacyClassification.CONFIDENTIAL
                        || route.privacyClassification() == PrivacyClassification.RESTRICTED)
                .toList();

        assertThat(sensitiveRoutes).isNotEmpty();
        for (RouteEntry route : sensitiveRoutes) {
            assertThat(route.auth())
                    .as("%s %s must not expose %s data publicly",
                            route.method(), route.path(), route.privacyClassification())
                    .isNotEqualTo(AuthMode.PUBLIC);
            assertThat(route.scopes())
                    .as("%s %s must carry explicit auth scopes",
                            route.method(), route.path())
                    .isNotEmpty();

            RouteAuthorizationRegistry.RouteDefinition definition = registry.getRouteDefinition(
                    HttpMethod.valueOf(route.method()),
                    route.path());

            assertThat(definition)
                    .as("%s %s must be present in the authorization registry", route.method(), route.path())
                    .isNotNull();
            assertThat(definition.privacyClassification().name())
                    .as("%s %s registry privacy must match manifest", route.method(), route.path())
                    .isEqualTo(route.privacyClassification().name());
        }
    }

    @Test
    @DisplayName("restricted and confidential response shaped payloads are redacted before serialization")
    void restrictedAndConfidentialResponsePayloadsAreRedactedBeforeSerialization() throws Exception {
        Map<String, Object> redacted = ServiceObservability.redactSensitiveFields(Map.of(
                "route", "/api/v1/yappc/evolve/proposal-1/approve",
                "privacyClassification", "RESTRICTED",
                "prompt", "build a private product for Customer Alpha",
                "generatedCode", "export const apiKey = 'sk-live-secret';",
                "policyDecision", Map.of(
                        "outcome", "DENY",
                        "credential", "oauth-refresh-token"),
                "diffs", List.of(Map.of(
                        "path", "src/App.tsx",
                        "content", "customer email jane@example.com")),
                "safeCounters", Map.of("tokenCount", 42, "evidenceCount", 2)));

        String json = MAPPER.writeValueAsString(redacted);

        assertThat(json)
                .contains(ServiceObservability.REDACTED_VALUE, "tokenCount", "evidenceCount")
                .doesNotContain(
                        "Customer Alpha",
                        "sk-live-secret",
                        "oauth-refresh-token",
                        "jane@example.com");
        assertThat(redacted)
                .containsEntry("prompt", ServiceObservability.REDACTED_VALUE)
                .containsEntry("generatedCode", ServiceObservability.REDACTED_VALUE);
    }

    @Test
    @DisplayName("public routes remain limited to public privacy classification")
    void publicRoutesRemainPublicOnly() {
        Set<PrivacyClassification> publicRouteClassifications = GeneratedRouteRegistry.getManifest()
                .getAllRoutes()
                .stream()
                .filter(route -> route.auth() == AuthMode.PUBLIC)
                .map(RouteEntry::privacyClassification)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(publicRouteClassifications).containsExactly(PrivacyClassification.PUBLIC);
    }
}
