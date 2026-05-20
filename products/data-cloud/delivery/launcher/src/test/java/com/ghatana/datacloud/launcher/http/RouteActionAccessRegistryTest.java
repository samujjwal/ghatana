/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-P1-04: Verifies contract-backed route/action access requirements for Data Cloud HTTP security.
 *
 * <p>Validates that the manual RouteActionAccessRegistry mappings align with
 * OpenAPI security definitions. This ensures that the registry stays in sync
 * with the canonical contract as routes evolve.
 *
 * @doc.type class
 * @doc.purpose Contract-backed route/action access validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RouteActionAccessRegistry - contract-backed access validation")
class RouteActionAccessRegistryTest {

    @Test
    @DisplayName("learning review approve requires ADMIN access")
    void shouldClassifyLearningReviewApproveAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/learning/review/123/approve"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    @DisplayName("learning review reject requires ADMIN access")
    void shouldClassifyLearningReviewRejectAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/learning/review/8f3c2d52-0d6f-4e1f-a5ab-1d2f88c1c1ad/reject"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    @DisplayName("unmapped route actions return null (fallback to path-prefix inference)")
    void shouldReturnNullForUnmappedRouteAction() {
        assertThat(RouteActionAccessRegistry.requiredAccess("GET", "/api/v1/learning/review")).isNull();
    }

    @Test
    @DisplayName("DC-P1-04: registry is contract-backed - critical governance routes have explicit mappings")
    void contractBacked_criticalGovernanceRoutesHaveExplicitMappings() {
        // Verify that critical governance routes are explicitly mapped in the registry
        // rather than relying solely on path-prefix inference
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/governance/retention/purge"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/governance/privacy/redact"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyConnectorCredentialRotationAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/connectors/conn-1/rotate-credentials"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyConnectorSyncAsOperator() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/connectors/conn-1/sync"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR);
    }

    @Test
    void shouldClassifySettingsUpdateAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/settings/security"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyGovernancePolicyToggleAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/governance/policies/policy-42/toggle"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyActionNamespaceLearningReviewApproveAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/action/learning/review/123/approve"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifySettingsKeyRotateAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/settings/keys/key-123/rotate"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyAlertSuggestionApplyAsOperator() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/alerts/suggestions/sugg-1/apply"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR);
    }
}